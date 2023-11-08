class StringTemplate1 {

  String x(int i) {
    return STR."""
       one two three \{i}
       four five six \{i<selection>}
       seven <caret>eight
       nine
       """</selection>;
  }
}