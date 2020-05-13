// "Replace 's' with pattern variable" "false"
class X {
  void test(Object obj) {
    if (!(obj instanceof String)) {
      String <caret>s = (String)obj;
    }
  }
}