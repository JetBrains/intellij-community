// "Simplify 'obj instanceof String str' to true" "true"
class Test {
  void test(Object obj) {
    if (obj instanceof String) {
      if (obj instanceof<caret> String str && str.isEmpty()) {

      }
      
      String str = "hello";
    }
  }
}