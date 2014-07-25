interface SAM {
    void bar(int i);
}

class Test {

  {
    SAM sam = (i) -> {};
  }

}
