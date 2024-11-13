// "Create field 'i' in 'A'" "true-preview"
class A {
    private int i<caret>;
    Object o = new Object() {

    public void f(A a) {
      a.i = 0;
