// "Remove 1st parameter from method 'f'" "true"
class A {
    void f() {}
    public void foo() {
        <caret>f();
    }
}