class Foreign {
}

class MoveMethodTest {
    void bar(Inner inner) {}

    class Inner {
      void <caret>foo(final Foreign foreign) {
         bar (this);
      }
    }
}
