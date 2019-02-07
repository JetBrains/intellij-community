public class PrivateFieldVsParam {
  public static void main(String[] args) {
    start(5);
  }

  static void start(int field) {
    new Cls() {
      @Override
      void foo() {
        System.out.println(<caret>field);
      }
    }.foo();
  }

  private static abstract class Cls {
    private String field = "xxx";

    abstract void foo();
  }
}