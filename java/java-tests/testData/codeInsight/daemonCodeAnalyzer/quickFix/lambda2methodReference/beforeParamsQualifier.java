// "Replace lambda with method reference" "false"
class Example {
  public void m(Example a) {
  }

  {
    A aA = (a) -> a.<caret>m(a);
  }
}

interface A {
  void a(Example a);
}