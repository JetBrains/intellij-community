// "Remove 2nd parameter from method 'f'" "true"
class A {
    void f(int i, int i2) {}
    public void foo() {
        <caret>f(1,1);
    }
}