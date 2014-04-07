interface SAM {
  void foo(int <caret>i);
}

class Test {

  {
    SAM sam = (i) -> {};
  }

}