// "Create field 'i' in 'A'" "false"
class A {
    Object o = new Object() {

    public void f(A a) {
      a.<caret>i = 0;
