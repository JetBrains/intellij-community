class StringTemplate1 {

  String x(int i) {
    return STR."one <caret>two three \{i} four five six \{i} seven eight nine}";
  }
}