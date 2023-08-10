public class OverloadedMember {
  static class Base {
    void method(int param) {
      helloBase(param);
    }
    private void helloBase(int param) {
    }
  }

  static class Impl extends Base {
    void method(int param<caret>) {
      helloImpl(<flown1>param);
      System.out.println("param = " + param);
    }
    private void helloImpl(int <flown11>param) {
    }
  }
}