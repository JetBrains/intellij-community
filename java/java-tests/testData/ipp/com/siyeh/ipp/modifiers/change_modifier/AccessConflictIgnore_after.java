public class Test {
  class X {
    protected void fooooooo() {}
  }

  class Y extends X {
    @Override
    protected void fooooooo() {
      super.fooooooo();
    }
  }
}