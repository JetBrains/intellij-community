class StringTemplate1 {

  String x(int i) {
    return STR."one two three \{i} four <caret>five six \{i} seven eight nine}";
  }
}