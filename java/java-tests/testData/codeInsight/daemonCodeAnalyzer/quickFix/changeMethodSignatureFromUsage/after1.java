// "Remove 1st parameter from method 'f'" "true-preview"
class A {
    void f() {}
    public void foo() {
        <caret>f();
    }
}