package com.wujunshen.elasticsearch;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.Property.Builder;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.util.ObjectBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wujunshen.ApplicationTests;
import com.wujunshen.config.ElasticSearchConfigProperties;
import com.wujunshen.entity.usergroup.User;
import com.wujunshen.entity.usergroup.UserGroup;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * @author frank woo(吴峻申) <br>
 *     email:<a href="mailto:frank_wjs@hotmail.com">frank_wjs@hotmail.com</a> <br>
 * @date 2020/2/7 5:33 下午 <br>
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(100)
public class UserGroupQueryTest extends ApplicationTests {
    private final ObjectMapper mapper = new ObjectMapper();
    @Resource
    private ElasticSearchConfigProperties elasticSearchConfigProperties;
    @Resource
    private IndexApi indexApi;
    @Resource
    private DocumentApi documentApi;
    @Resource
    private QueryApi queryApi;
    private String indexName;

    @BeforeAll
    public void init() throws IOException {
        indexName = elasticSearchConfigProperties.getIndex();

        if (indexApi.isExistedIndex(indexName)) {
            indexApi.deleteIndex(indexName);
        }

        //        String mapping = "{\n"
        //                + "  \"properties\": {\n"
        //                + "    \"id\": {\n"
        //                + "      \"type\": \"long\"\n"
        //                + "    },\n"
        //                + "    \"user\": {\n"
        //                + "      \"type\": \"nested\",\n"
        //                + "      \"properties\": {\n"
        //                + "        \"last\": {\n"
        //                + "          \"type\": \"keyword\"\n"
        //                + "        },\n"
        //                + "        \"id\": {\n"
        //                + "          \"type\": \"long\"\n"
        //                + "        },\n"
        //                + "        \"first\": {\n"
        //                + "          \"type\": \"keyword\"\n"
        //                + "        }\n"
        //                + "      }\n"
        //                + "    },\n"
        //                + "    \"group\": {\n"
        //                + "      \"fielddata\": true,\n"
        //                + "      \"type\": \"text\"\n"
        //                + "    }\n"
        //                + "  }\n"
        //                + "}";
        //        indexApi.createIndexWithMapping(indexName, mapping);

        Function<Builder, ObjectBuilder<Property>> userFn =
                fn -> fn.nested(user -> user.properties("id", id -> id.long_(longProperty -> longProperty.index(true)))
                        .properties("first", first -> first.keyword(textProperty -> textProperty.index(true)))
                        .properties("last", last -> last.keyword(textProperty -> textProperty.index(true))));

        TypeMapping typeMapping = new TypeMapping.Builder()
                .properties("id", id -> id.long_(longProperty -> longProperty.index(true)))
                .properties("group", group -> group.text(textProperty -> textProperty.fielddata(true)))
                .properties("user", userFn)
                .build();

        indexApi.createIndexWithMapping(indexName, typeMapping);

        // 创建数据
        User user = new User();
        user.setId(1L);
        user.setFirst("junshen");
        user.setLast("wu");

        UserGroup userGroup = new UserGroup();
        userGroup.setId(1L);
        userGroup.setGroup("fans");
        userGroup.setUser(user);

        documentApi.addDocument(indexName, userGroup);

        indexApi.refresh(indexName);
    }

    @AfterAll
    public void clear() throws IOException {
        indexApi.deleteIndex(indexName);

        indexName = null;
    }

    /** 嵌套查询, 内嵌文档查询 */
    @Test
    public void nestedQuery() throws IOException {
        // 准备查询
        Query query = Query.of(q -> q.bool(t -> t.must(List.of(
                Query.of(q1 -> q1.match(t1 -> t1.field("user.first").query("junshen"))),
                Query.of(q2 -> q2.match(t2 -> t2.field("user.last").query("wu")))))));

        List<UserGroup> result = queryApi.nestedQuery(
                indexName, "user", query, ChildScoreMode.None, "id", 0, 10, true, UserGroup.class);

        log.info("\njson string is:{}，list size is:{}\n", mapper.writeValueAsString(result), result.size());

        assertThat(result, notNullValue());
        assertThat(result.size(), equalTo(1));
    }
}
