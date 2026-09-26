// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

class Test {
  String foo;
  
  String select() {
    return foo !<caret>= null ? this.foo.trim() : "";
  }
}