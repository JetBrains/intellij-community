class Test {
  static int variable = 1;
  static class Inner{
    int variable = 0;
    static void foo(){
      int val1 = <ref>variable;
    }
  }
}
