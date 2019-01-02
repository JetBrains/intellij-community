  class Foo {
      private void f() {
          Bar bar=null;
          bar.<caret>f();
      }
      void g() {
          f();
      }
  }
  class Bar extends Foo {
  }
