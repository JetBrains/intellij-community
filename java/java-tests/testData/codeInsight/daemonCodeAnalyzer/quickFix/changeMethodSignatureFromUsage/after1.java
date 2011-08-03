// "Remove 1 parameter from method f" "true"
class A {
    void f() {}
    public void foo() {
        <caret>f();
    }
}