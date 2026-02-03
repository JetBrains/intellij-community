// "Add constructor parameter" "true"
class A {
    private final A <caret>field;
    public A(int x) {
        int g=0;
    }
}