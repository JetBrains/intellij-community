interface SAM {
  void <caret>foo();
}

class Test {
  {
    SAM sam = () -> {};
  }
}