// "Replace with Optional.ofNullable() chain" "false"

class Test {
  String foo;
  
  String select(Test other) {
    return this.foo !<caret>= null ? other.foo.trim() : "";
  }
}