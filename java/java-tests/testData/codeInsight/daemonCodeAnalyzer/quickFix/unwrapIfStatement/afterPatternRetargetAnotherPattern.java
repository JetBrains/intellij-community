// "Simplify 'obj instanceof String str' to true" "true"
class Test {
  void test(Object obj) {
    if (obj instanceof String s) {
      if (s.isEmpty()) {

      }
      System.out.println(s);
    }
  }
}