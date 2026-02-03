// "<html> Change signature of f(<s>int</s>, <s>String</s>)</html>" "true-preview"
class A {
    void f() {}
    public void foo() {
        <caret>f();
    }
}