// "Simplify 'obj instanceof String str' to true" "true"
class Test {
  void test(Object obj) {
    if (obj instanceof String s) {
      if (obj instanceof<caret> String str && str.isEmpty()) {

      }
      System.out.println(s);
    }
  }
}