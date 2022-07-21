// "Remove 1st parameter from method 'f'" "true-preview"
class A {
    void f(int i) {}
    public void foo() {
        <caret>f();
    }
}