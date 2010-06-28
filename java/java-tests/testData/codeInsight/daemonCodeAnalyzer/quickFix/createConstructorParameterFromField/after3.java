// "Add constructor parameter" "true"
class A {
    private A field;
    public A(int x, A field) {
        this.field = field;
        int g=0;
    }
    A(A field) {
      this("", field);
    }
    A(String s, A field) {
      //here
        this.field = field;<caret>
    }
}