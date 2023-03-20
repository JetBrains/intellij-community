// "Replace 'switch' with 'if'" "true-preview"
class Test {
  void test(String str) {
    <caret>switch (str) {
      case "foo" + "bar":
    }
  }
}