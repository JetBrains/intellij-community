public class DuplicateWithAnonymousFieldReference {

  void test(){
      Runnable task = new Runnable() {
          @Override
          public void run() {
              extracted();

              extracted();
          }

          private void extracted() {
              System.out.println(myField);
          }

          final int myField = 42;
      };
  }
}
