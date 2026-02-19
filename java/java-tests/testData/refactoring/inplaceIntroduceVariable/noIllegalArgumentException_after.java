public class GraphQLIntrospectionQuery {

  String INTROSPECTION_QUERY;

    {
        String s = "fragment TypeRef on __Type {\n" +
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
                "\n";
        INTROSPECTION_QUERY = s;
    }
}