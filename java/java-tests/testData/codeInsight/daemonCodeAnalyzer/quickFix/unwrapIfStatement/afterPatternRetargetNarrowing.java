// "Simplify 'obj instanceof CharSequence cs' to true" "true"
class X {
  void test(Object obj) {
    if (obj instanceof String cs) {
      if (cs.length() > 0) {}
    }
  }
}