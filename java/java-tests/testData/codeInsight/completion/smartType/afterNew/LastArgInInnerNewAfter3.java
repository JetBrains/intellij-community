class AAA {
  AAA(int i) {}
}

class B {
    int iiii=0;
    B(int i,AAA aaa, int j, int k) {
      B b = new B(
        0,
        new AAA(iiii),
      );
    }
}