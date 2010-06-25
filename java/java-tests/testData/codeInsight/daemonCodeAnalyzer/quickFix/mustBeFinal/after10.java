// "Copy 'a' to temp final variable" "true"
public class DoubleTrouble {
    public void test() {
        int a = 1;
        a = 2;
        final int finalA = a;
        class s {
          public void run() {
            new Runnable() {
              public void run() {
                System.out.println(<caret>finalA);
              }
            }.run();
          }
        }
      }

}
