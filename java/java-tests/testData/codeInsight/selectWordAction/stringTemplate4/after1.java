class StringTemplate1 {

  String x(int i) {
    return STR."""
       one <selection><caret>two</selection>
       three \{i}
       four five six \{i}
       seven eight nine
       """;
  }
}