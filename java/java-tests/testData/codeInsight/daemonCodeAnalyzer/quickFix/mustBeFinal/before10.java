// "Copy 'a' to final temp variable" "true"
public class DoubleTrouble {
    public void test() {
        int a = 1;
        a = 2;
        class s {
          public void run() {
            new Runnable() {
              public void run() {
                System.out.println(<caret>a);
              }
            }.run();
          }
        }
      }

}
