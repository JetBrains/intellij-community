// "Change signature of 'f(int, String, int)' to 'f(int, int)'" "true"
class A {
    void f(int i, int i2) {}
    public void foo() {
        <caret>f(1,1);
    }
}