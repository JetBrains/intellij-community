class Outer {
  int i;
  class Inner extends Outer {
    {
      int j = <caret>i;
    }
  }
}