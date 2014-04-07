interface SAM {
  void fo<caret>o(int i);
}

class Test {

  {
    SAM sam = (i) -> {};
  }

}