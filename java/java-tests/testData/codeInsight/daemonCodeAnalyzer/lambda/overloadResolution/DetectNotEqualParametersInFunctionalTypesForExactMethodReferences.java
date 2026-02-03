interface I1 {
  int i1(int i);
}

interface I2 {
  Integer i2(Integer i);
}

interface I3 {
  Integer i3(int i);
}

class Test {

  private void <warning descr="Private method 'm(I1)' is never used">m</warning>(I1 i1) {System.out.println(i1);}
  private void <warning descr="Private method 'm(I2)' is never used">m</warning>(I2 i2) {System.out.println(i2);}

  private void m1(I1 i1) {System.out.println(i1);}
  private void <warning descr="Private method 'm1(I3)' is never used">m1</warning>(I3 i2) {System.out.println(i2);}

  void test() {
    m <error descr="Ambiguous method call: both 'Test.m(I1)' and 'Test.m(I2)' match">(this::bar)</error>;
    m1(this::bar);
  }

  int bar(int i) {
    return i;
  }
}
