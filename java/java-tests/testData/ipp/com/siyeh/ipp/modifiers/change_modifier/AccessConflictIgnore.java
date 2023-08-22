public class Test {
  class X {
    void <caret>fooooooo() {}
  }

  class Y extends X {
    @Override
    void fooooooo() {
      super.fooooooo();
    }
  }
}