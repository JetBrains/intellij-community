package pck;

class A {
  <T> void foo(T x) {
    foo(1);

    long x1 = 1L;
    foo(x1);
    
    Long x2 = 1L;
    foo(x2);
    
    Integer x3 = 1;
    foo(x3);
  }

  void foo(long x) {
  }
}
