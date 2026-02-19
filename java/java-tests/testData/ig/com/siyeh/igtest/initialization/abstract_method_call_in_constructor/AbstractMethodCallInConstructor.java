abstract class A {
  public A() {
    Runnable r = () -> foo();
    Runnable r1 = new Runnable() {
      public void run() {
        foo();
      }
    };
    <warning descr="Call to 'abstract' method 'foo()' during object construction">foo</warning>();
  }

  abstract void foo();
}