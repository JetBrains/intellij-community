// "Remove redundant 'toString()' call" "true-preview"
class X {
  void test(Object x) {
    System.out.println(x.toStri<caret>ng());
  }
}