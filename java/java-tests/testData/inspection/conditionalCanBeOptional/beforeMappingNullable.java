// "Replace with Optional.ofNullable() chain (may change semantics)" "INFORMATION"

class Test {
  String trim(String s) {
    return s.isEmpty() ? null : s.trim();
  }

  String select(String foo) {
    return foo !<caret>= null ? trim(foo) : "";
  }
}