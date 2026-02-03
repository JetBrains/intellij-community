// "Simplify 'obj instanceof String str' to true" "true"
class Test {
  void test(Object obj) {
    if (!(obj instanceof String) || obj<caret> instanceof String str && str.isEmpty()) {

    }
  }
}