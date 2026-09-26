class SlashSlashInLiteral {
  final String s1 = // comment
          "jdbc:mysql://" + "a" +
                  '/' + "b" +
                  '?' + "user" + '=' + "c" +
                  "&amp;" + "password" + '=' + "d";
}