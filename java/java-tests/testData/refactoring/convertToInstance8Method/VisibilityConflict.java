class Test {
  {
    foo(new Bar());
  }
  private static void <caret>foo(Bar b){}

}

class Bar {

}
