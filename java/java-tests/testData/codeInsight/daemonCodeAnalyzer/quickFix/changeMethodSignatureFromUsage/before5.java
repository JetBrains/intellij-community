// "Remove 2nd parameter from method 'f'" "true-preview"
class A {
    void f(int i, String s, int i2) {}
    public void foo() {
        <caret>f(1,1);
    }
}