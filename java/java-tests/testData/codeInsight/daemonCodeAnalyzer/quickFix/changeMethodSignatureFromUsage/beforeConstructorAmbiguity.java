// "<html> Change signature of A(<s>int</s>)</html>" "true-preview"
class A {
    A(int i) {}
    A(int i, String s) {}
    public void foo() {
        new A<caret>();
    }
}