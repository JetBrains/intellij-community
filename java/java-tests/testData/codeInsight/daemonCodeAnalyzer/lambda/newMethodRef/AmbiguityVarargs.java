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
      m(this ::<error descr="Cannot resolve method 'ii'">ii</error>);
  }

  int ii() {return 0;}

}
