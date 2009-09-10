class A {
    private void f() {}
}

class B {
    private A b;
    public void g() {
        b.<caret>f();
    }
}