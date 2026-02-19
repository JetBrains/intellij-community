public class GraphQLIntrospectionQuery {

  String INTROSPECTION_QUERY = <selection>"fragment TypeRef on __Type {\n" +
                               "    kind\n" +
                               "    name\n" +
                               "    ofType {\n" +
                               "      kind\n" +
                               "      name\n" +
                               "      ofType {\n" +
                               "        kind\n" +
                               "        name\n" +
                               "        ofType {\n" +
                               "          kind\n" +
                               "          name\n" +
                               "          ofType {\n" +
                               "            kind\n" +
                               "            name\n" +
                               "            ofType {\n" +
                               "              kind\n" +
                               "              name\n" +
                               "              ofType {\n" +
                               "                kind\n" +
                               "                name\n" +
                               "                ofType {\n" +
                               "                  kind\n" +
                               "                  name\n" +
                               "                }\n" +
                               "              }\n" +
                               "            }\n" +
                               "          }\n" +
                               "        }\n" +
                               "      }\n" +
                               "    }\n" +
                               "  }\n" +
                               "\n"</selection>;}