class Foreign {
}

class MoveMethodTest {
    void bar() {}

    void <caret>foo(final Foreign foreign) {
        class Inner {
            {
                MoveMethodTest.this.bar();
            }
        }
    }
}
