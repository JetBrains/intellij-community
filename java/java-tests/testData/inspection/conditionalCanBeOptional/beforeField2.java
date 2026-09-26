// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

class Test {
  String foo;
  
  String select() {
    return this.foo !<caret>= null ? foo.trim() : "";
  }
}