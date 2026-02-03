// "Simplify 'obj instanceof String str' to true extracting side effects" "true"
class Test {
  void test(Object obj) {
    if (obj instanceof String s) {
      s = s.trim();
      if (obj instanceof<caret> String str && str.isEmpty()) {

      }
      System.out.println(s);
    }
  }
}