// "Replace 'switch' with 'if'" "true"
class Test {
  void test(String str) {
    <caret>switch (str) {
      case "foo" + "bar":
    }
  }
}