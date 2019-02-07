// "<html> Change signature of f(<s>int</s> <b>String</b>, <s>String</s> <b>int</b>)</html>" "false"
class A {
    void f(int i, String s) {}
    public void foo() {
        <caret>f("", 1);
    }
}