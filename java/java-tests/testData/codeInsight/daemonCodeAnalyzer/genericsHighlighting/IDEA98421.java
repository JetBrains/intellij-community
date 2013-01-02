class Outer {
  class Inner {
    public <T> Inner(T t) { }
  }

  class Other extends Outer.Inner {
    public Other() {
      new Outer().<Object>super("Hi");
    }
  }
}
