// "Change signature of 'f(int)' to 'f()'" "true"
class A {
    void f() {}
    public void foo() {
        <caret>f();
    }
}