class ChangeSignatureTest {
    void <caret>foo() {
    }

    void bar() {
      foo();
    }

    {
        bar();
    }
}