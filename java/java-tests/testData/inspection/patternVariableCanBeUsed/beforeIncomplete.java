// "Fix all 'Pattern variable can be used' problems in file" "false"
class X {
  void test(Object obj) {
    if() {
      String <caret>s = (String) obj;
    }
  }
}