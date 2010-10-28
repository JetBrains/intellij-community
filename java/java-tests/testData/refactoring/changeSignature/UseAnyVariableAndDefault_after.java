class C {
    C(C c){}
}

class Usage {
    void foo() {
      C c1 = new C(null);
      C c2 = new C(c1);
    }

    void bar() {
      C c1 = new C(null), c2 = new C(c1);
    }
}