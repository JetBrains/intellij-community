class StringTemplate1 {

  String x(int i) {
    return STR."""
       one two three \{i}
       four <selection><caret>five</selection>
       six \{i}
       seven eight nine
       """;
  }
}