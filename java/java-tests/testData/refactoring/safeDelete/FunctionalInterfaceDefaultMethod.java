interface SAM {
  default void fo<caret>o(int i){}
  void bar(int i);
}

class Test {

  {
    SAM sam = (i) -> {};
  }

}
