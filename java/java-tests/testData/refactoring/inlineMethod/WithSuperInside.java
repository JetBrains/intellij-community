public class Usage {
  void foo() {
    b<caret>ar();
  }

  void bar() {
    new W() {
      protected void www() {
        super.www();
      }
    };
  }
}

class W {
  protected void www() {}
}