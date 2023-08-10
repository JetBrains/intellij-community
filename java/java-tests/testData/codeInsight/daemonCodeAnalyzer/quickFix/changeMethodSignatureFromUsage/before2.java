// "<html> Change signature of f(<s>int</s>, <s>String</s>)</html>" "true-preview"
class A {
    void f(int i,String s) {}
    public void foo() {
        <caret>f();
    }
}