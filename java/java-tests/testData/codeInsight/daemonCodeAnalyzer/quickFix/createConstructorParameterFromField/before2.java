// "Add constructor parameter" "true"
class A {
    private A <caret>field;
    public A(int x) {
        //this.field = field;
    }
}