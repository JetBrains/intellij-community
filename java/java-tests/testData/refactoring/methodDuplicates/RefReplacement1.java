class Test {
    void bar() {}

    public void main() {
        bar();
    }
}

class Test1 {
  void <caret>foo(Test t) {
      t.bar();
  }
}