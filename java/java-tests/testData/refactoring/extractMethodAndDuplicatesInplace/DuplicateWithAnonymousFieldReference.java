public class DuplicateWithAnonymousFieldReference {

  void test(){
      Runnable task = new Runnable() {
          @Override
          public void run() {
              <selection>System.out.println(myField);</selection>

              System.out.println(myField);
          }

          final int myField = 42;
      };
  }
}
