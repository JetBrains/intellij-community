// "Add constructor parameter" "true"
class A {
    private final A field;
    public A(int x, A field) {
        this.field = field;<caret>
        int g=0;
    }
}