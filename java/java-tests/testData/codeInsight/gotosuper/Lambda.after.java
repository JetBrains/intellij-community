interface I {
  void <caret>run();
}

class Foo {
  {
    I i = () -> {};
  }
}