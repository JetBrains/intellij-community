class Test {
  interface IInt {
    int _();
  }

  interface ILong {
    long _();
  }

  void m(IInt i, Long l) {}
  void m(ILong l, Integer i) {}

  void m1(IInt i, Integer l) {}
  void m1(ILong l, Object i) {}

  void test() {
    m<error descr="Ambiguous method call: both 'Test.m(IInt, Long)' and 'Test.m(ILong, Integer)' match">(() -> 1, null)</error>;
    m1(() -> 1, null);
  }
}