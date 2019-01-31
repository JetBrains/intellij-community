interface A {
    default void <caret>m() {}
    default void f() {
        m();
    }
}

class Client1 implements A {
    Client2 a;
    @Override
    public void m() {
        a.m();
    }
}

class Client2 implements A {
    @Override
    public void f() { }
}
