class C {
    <caret>C(String name){}
}

class Usage {
    void foo() {
      C c1 = new C("1");
      C c2 = new C("2");
    }

    void bar() {
      C c1 = new C("1"), c2 = new C("2");
    }
}