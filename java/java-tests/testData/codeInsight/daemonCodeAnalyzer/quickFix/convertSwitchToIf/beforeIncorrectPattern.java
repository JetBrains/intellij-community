// "Replace 'switch' with 'if'" "true"
class Test {
  void test(Object obj) {
    <caret>switch (obj) {
      case obj instanceof String s -> {}
    }
  }
}