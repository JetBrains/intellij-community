// "Remove expression" "true-preview"
class X {
  void test() {
    for (int i = 0; i < Integer.MAX_VALUE; i<caret> *= 2) {

    }
  }
}