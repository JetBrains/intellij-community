// "Surround with array initialization" "false"
class A {
  void m1(String[] s,
          String[] s2,
          String[] s3) {}
    {
        m1( <caret>null, null);
    }
}