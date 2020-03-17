// "Fix all 'Pattern variable can be used' problems in file" "true"
class X {
  void test(Object obj, Object obj2) {
    if (obj instanceof String s && obj2 instanceof Integer i) {
    }
  }
}