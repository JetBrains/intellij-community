// "Replace lambda with method reference" "true"
class Example extends O {

    void m(Runnable r) {}

    class A {
      class B {
        {
          m(() -> p<caret>());
        }
      }
    }
}

class O {
  void p() {}
}