class StringTemplate1 {

  String x(int i) {
    return STR."""
       one two three \{i}<selection>
       four <caret>five
       six </selection>\{i}
       seven eight nine
       """;
  }
}