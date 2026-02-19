class StringTemplate1 {

  String x(int i) {
    return STR.<selection>"""
       one <caret>two
       three \{i}
       four five six \{i}
       seven eight nine
       """</selection>;
  }
}