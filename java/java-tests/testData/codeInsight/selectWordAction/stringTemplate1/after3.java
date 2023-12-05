class StringTemplate1 {

  String x(int i) {
    return STR.<selection>"one <caret>two three \{</selection>i} four five six \{i} seven eight nine}";
  }
}