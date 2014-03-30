class Test {

  interface IntMapper {
    int map();
  }

  interface LongMapper {
    long map();
  }

  void m(IntMapper im1, IntMapper... im) { }
  void m(LongMapper... lm) { }
  
  {
      m<error descr="Ambiguous method call: both 'Test.m(IntMapper, IntMapper...)' and 'Test.m(LongMapper...)' match">(this ::ii)</error>;
  }

  int ii() {return 0;}

}
