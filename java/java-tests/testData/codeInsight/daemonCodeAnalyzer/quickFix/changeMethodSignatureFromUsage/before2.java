// "Change signature of 'f(int, String)' to 'f()'" "true"
class A {
    void f(int i,String s) {}
    public void foo() {
        <caret>f();
    }
}