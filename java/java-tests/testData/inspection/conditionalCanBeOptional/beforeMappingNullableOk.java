// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

class Test {
  String trim(String s) {
    return s.isEmpty() ? null : s.trim();
  }

  String select(String foo) {
    return foo !<caret>= null ? trim(foo) : null;
  }
}