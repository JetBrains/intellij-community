// "Change signature of 'f(int, String, int)' to 'f(int, int, int)'" "true"
class A {
    void f(int i, String s, int i2) {}
    public void foo() {
        <caret>f(1,1,'4');
    }
}