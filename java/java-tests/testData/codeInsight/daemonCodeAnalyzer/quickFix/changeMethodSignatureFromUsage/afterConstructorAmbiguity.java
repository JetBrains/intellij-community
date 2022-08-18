// "<html> Change signature of A(<s>int</s>)</html>" "true-preview"
class A {
    A() {}
    A(int i, String s) {}
    public void foo() {
        new A();
    }
}