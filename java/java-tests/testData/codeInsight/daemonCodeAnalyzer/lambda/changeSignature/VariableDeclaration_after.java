interface SAM {
  void foo(boolean b);
}

class Test {
  {
    SAM sam = (boolean b) -> {};
  }
}