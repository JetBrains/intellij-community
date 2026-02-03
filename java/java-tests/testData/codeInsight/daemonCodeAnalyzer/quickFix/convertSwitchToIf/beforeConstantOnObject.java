// "Replace 'switch' with 'if'" "true-preview"
class Test {
  void test(Object obj) {
    <caret>switch (obj) {
      case 1 -> {}
    }
  }
}