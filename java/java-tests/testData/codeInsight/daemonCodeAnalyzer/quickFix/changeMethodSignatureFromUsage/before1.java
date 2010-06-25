// "Change signature of 'f(int)' to 'f()'" "true"
class A {
    void f(int i) {}
    public void foo() {
        <caret>f();
    }
}