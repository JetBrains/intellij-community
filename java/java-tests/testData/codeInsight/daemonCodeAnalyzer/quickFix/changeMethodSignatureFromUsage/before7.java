// "Change signature of 'f(int, String, int, int, char, String)' to 'f(int, int, String)'" "true"
class A {
    void f(int i, String s, int i2, int i3, char c, String s) {}
    public void foo() {
        <caret>f(1,1,"4");
    }
}