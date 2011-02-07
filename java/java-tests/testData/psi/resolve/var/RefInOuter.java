class Outer {
  int i;
  class Inner {
    {
      int j = <ref>i;
    }
  }
}