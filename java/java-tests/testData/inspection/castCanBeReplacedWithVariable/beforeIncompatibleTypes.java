// "Replace '(Integer) obj' with 's'" "false"

class X {
  void test(Object obj) {
    String s = (String) obj;
    Integer i = ((Integer) ob<caret>j).intValue();
  }
}