// "Replace lambda with method reference" "false"
class Example {
  interface I {
    void foo(Example e);
  }

  void m() {}

  {
    I i = (e) -> <caret>.m();
  }
}