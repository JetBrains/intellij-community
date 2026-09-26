// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

class Test {
  String foo;
  
  String select(Test other) {
    return this.foo !<caret>= null ? other.foo.trim() + foo.trim() : other.foo+this.foo;
  }
}