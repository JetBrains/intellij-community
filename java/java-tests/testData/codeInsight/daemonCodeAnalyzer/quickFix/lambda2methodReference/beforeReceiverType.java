// "Replace lambda with method reference" "true-preview"
class Example {
  interface I {
    void foo(Example e);
  }

  void m() {}

  {
    I i = (e) -> e<caret>.m();
  }
}