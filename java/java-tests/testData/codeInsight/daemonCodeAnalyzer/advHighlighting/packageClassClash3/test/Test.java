package test;

class Test {
  void test() {
    pkg s0;
    pkg.<error descr="Cannot resolve symbol 'Sub1'">Sub1</error> s1;
    pkg.sub.<error descr="Cannot resolve symbol 'Sub2'">Sub2</error> s2;
  }

  public static class pkg { }  // an unfortunate name
}