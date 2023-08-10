// "Remove 2nd parameter from method 'f'" "true-preview"
class A {
    void f(int i, int i2) {}
    public void foo() {
        <caret>f(1,1);
    }
}