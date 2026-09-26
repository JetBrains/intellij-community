// "Remove argument" "true-preview"
class Foo {
  int test(String foo) {
    return foo.indexOf("bar", <caret>0);
  }
}