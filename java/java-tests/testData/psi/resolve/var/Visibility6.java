  class Foo {
      private void f() {
          Bar bar=null;
          bar.<ref>f();
      }
      void g() {
          f();
      }
  }
  class Bar extends Foo {
  }
