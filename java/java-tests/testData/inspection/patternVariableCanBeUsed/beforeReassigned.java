// "Replace 's' with pattern variable" "true"
class X {
  void test(Object obj) {
    if (obj instanceof String) {
      String <caret>s = (String)obj;
      System.out.println(s);
      s = s.trim();
    }
  }
}