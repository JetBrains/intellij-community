// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

class Test {
  String select(String foo) {
    return foo !<caret>= null ? foo.trim() : "";
  }
}