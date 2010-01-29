public class A {
  Thread m() {
    final int x<caret> = 42;
    return new Thread() {
      int y = 23;

      public void run() {
        System.out.println(x);
      }
    };
  }

  public static void main(String[] args) {
    new A().m().start();
  }
}