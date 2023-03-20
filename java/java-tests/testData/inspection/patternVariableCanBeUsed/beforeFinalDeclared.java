// "Replace 's' with pattern variable" "true"
class X {
  void test(Object obj) {
    if (obj instanceof String) {
      @Ann final String <caret>s = (String)obj;
    }
  }
}