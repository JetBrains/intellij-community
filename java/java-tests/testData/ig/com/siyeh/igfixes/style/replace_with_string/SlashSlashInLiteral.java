class SlashSlashInLiteral {
  final String s1 = new <caret>StringBuilder(34) // comment
    .append("jdbc:mysql://").append("a")
    .append('/').append("b")
    .append('?').append("user").append('=').append("c")
    .append("&amp;").append("password").append('=').append("d")
    .toString();
}