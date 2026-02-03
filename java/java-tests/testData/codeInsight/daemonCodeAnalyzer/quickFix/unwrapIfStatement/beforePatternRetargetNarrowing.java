// "Simplify 'obj instanceof CharSequence cs' to true" "true"
class X {
  void test(Object obj) {
    if (obj instanceof String) {
      if (obj instanceof CharSequence <caret>cs && cs.length() > 0) {}
    }
  }
}