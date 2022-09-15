// "Replace 'integer' with existing pattern variable 'i'" "false"
class X {
  void test(Object obj) {
    if (obj instanceof Integer i) {
      i = 42;
      Integer <caret>integer = (Integer)obj;
    }
  }
}