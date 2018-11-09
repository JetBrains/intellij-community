// "Remove argument" "true"
class Foo {
  boolean test(String foo) {
    return foo.startsWith("bar", (<caret>0));
  }
}