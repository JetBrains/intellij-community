// "Replace 's' with pattern variable" "true"
class X {
  void test(Object obj) {
    if (obj instanceof String) {
      final String <caret>s = (String)obj;
    }
  }
}