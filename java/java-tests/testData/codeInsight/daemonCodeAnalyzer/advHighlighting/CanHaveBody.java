abstract public class a1 {
  <error descr="Abstract methods cannot have a body">abstract void f()</error> {}
  <error descr="Native methods cannot have a body">native void ff()</error> {}

  void f2() {
    new ii() {
      public int iif(int i) {
        return 0;
      }
    };
  }
}

interface ii {
  <error descr="Interface abstract methods cannot have body">int iif(int i) throws Exception</error> { return 2; }
}
