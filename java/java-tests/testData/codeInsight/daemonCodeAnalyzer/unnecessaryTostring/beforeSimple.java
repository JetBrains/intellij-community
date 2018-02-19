// "Fix all 'Unnecessary call to 'toString()'' problems in file" "true"
class X {
  void test(Object x) {
    System.out.println(x.toStri<caret>ng());
  }
}