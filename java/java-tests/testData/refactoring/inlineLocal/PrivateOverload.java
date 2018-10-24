class Base {
    private void f() {}

    static void g() {
        Base b = new Derived();
        <caret>b.f();
    }
}

class Derived extends Base {
    void f() {}
}