class Outer {
  int i;
  class Inner {
    {
      int j = <caret>i;
    }
  }
}