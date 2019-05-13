// "Replace lambda with method reference" "false"
class Example {
  {
    new Object() {
      void foo() {
      }

      void bar() {
        new Object() {
          void baz() {
            Runnable runnable = () -> fo<caret>o();
          }
        };
      }
    };
  }
}