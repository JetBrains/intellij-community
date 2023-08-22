
class Outer {
  Comparable<String> f() {
    return new Comparable<>() {
      @Override
      public int compareTo(String o) {
        return 0;
      }
    };
  }
  
  interface I<T> {
    void m();
  }
  
  {
    I<String> i1 = new I<>() {
      @Override
        public void m() {}
    };
    I<String> i2 = new I<error descr="Cannot use '<>' due to non-private method which doesn't override or implement a method from a supertype"><></error>() {
        @Override
        public void m() {}
        public void m1() {}
    };
  }
}