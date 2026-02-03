class C {
  static void bar() {
    class Visitor {
      int field;

      void foo() {
        field<caret>
      }
    }
  }
}