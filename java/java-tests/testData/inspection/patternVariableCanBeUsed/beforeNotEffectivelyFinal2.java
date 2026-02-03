// "Replace 'integer' with existing pattern variable 'i'" "false"
class X {
  void test(Object obj) {
    if (obj instanceof final Integer i) {
      Integer <caret>integer = (Integer)obj;
      integer = 42;
    }
  }
}