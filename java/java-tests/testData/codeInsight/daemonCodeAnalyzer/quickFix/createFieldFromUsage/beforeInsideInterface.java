// "Create field 'myFoo'" "false"

interface Test {
  default String get() {
    return my<caret>Foo;
  }
}