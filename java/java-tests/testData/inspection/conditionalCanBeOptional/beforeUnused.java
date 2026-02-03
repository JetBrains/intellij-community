// "Replace with Optional.ofNullable() chain" "false"

class Test {
  String select(String foo, String bar) {
    return foo =<caret>= null ? bar : bar.trim();
  }
}