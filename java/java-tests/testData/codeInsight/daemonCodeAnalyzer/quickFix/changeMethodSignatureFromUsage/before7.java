// "<html> Change signature of f(int, <s>String</s>, int, <s>int</s>, <s>char</s>, String)</html>" "true"
class A {
    void f(int i, String s, int i2, int i3, char c, String s) {}
    public void foo() {
        <caret>f(1,1,"4");
    }
}