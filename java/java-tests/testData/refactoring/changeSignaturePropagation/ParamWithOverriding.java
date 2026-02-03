class ChangeSignatureTest {
    public <caret>ChangeSignatureTest() {
    }

    void foo() {
        new ChangeSignatureTest();
    }

    {
      foo();
    }
}

class Derived extends ChangeSignatureTest {
  void foo () {}
}