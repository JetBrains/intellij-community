// "<html> Change signature of f(<s>int</s>, <s>String</s>)</html>" "true"
class A {
    void f() {}
    public void foo() {
        <caret>f();
    }
}