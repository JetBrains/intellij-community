class B {
    void test() {
      new B() {
        void <caret>foo() { }
      };
    }
}