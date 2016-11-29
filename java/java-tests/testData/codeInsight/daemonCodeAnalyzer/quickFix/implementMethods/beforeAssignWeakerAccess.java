// "Implement methods" "true"
class B {
    protected void f() {}
}
interface A {
    void f();
}
<caret>class D extends B implements A {}