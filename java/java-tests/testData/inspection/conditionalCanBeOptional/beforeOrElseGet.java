// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

class Test {
  String getDefault() {
    return "";
  }

  String select(String foo) {
    return foo =<caret>= null ? getDefault() : foo.trim();
  }
}