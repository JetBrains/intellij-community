// "Change signature of 'f(int, String)' to 'f()'" "true"
class A {
    void f() {}
    public void foo() {
        <caret>f();
    }
}