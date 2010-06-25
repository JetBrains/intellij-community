// "Change signature of 'f(int, String, int, int, char, String)' to 'f(int, int, String)'" "true"
class A {
    void f(int i, int i2, String s) {}
    public void foo() {
        <caret>f(1,1,"4");
    }
}