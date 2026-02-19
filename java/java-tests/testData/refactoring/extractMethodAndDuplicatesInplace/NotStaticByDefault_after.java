public class Test {

  void test(int x) {
      new Runnable() {

          @Override
          public void run() {
              extracted();
          }

          private void extracted() {
              System.out.println(x);
          }
      };
  }
}