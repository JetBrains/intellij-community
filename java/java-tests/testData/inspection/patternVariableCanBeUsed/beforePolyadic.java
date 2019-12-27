// "Fix all 'Pattern variable can be used' problems in file" "true"
class X {
  void test(Object obj, Object obj2) {
    if (obj instanceof String && obj2 instanceof Integer) {
      String <caret>s = (String)obj;
      Integer i = (Integer)obj2;
    }
  }
}