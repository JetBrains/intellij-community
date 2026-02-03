// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

class Test {
  String select(String foo, String bar) {
    return foo =<caret>= null ? bar : foo;
  }
}