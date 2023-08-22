// "Remove argument" "true-preview"
class Foo {
  int test(String foo) {
    return foo.lastIndexOf("bar", <caret>((foo.length())-1));
  }
}