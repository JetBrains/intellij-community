// "Add constructor parameter" "true"
class A {
    private A <caret>field;
    public A(int x) {
        int g=0;
    }
    A() {
      this("");
    }
    A (String s) {
      //here
    }
}