// "Create field 'i' in 'A'" "true" 
class A {
    private int i<caret>;
    Object o = new Object() {

    public void f(A a) {
      a.i = 0;
