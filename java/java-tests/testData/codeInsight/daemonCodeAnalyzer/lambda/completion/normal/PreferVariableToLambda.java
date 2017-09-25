interface I {
  void foo(String out);
}

class Foo {
  I output;

  {
    I r = out<caret>
  }
}