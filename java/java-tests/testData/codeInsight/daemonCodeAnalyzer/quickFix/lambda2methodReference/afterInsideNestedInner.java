// "Replace lambda with method reference" "true-preview"
class Example {
  class Bar {
    void foo() {
    }

    class Foo {

      void bar() {
        new Object() {
          void baz() {
            Runnable runnable = Bar.this::foo;
          }
        };
      }
    }
  }
}