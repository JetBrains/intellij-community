// "Collapse 'if' statement" "true"

import java.util.List;
import java.util.Map;

public class Main {

  void test(int x) {
    if<caret>(x > 0) {
      Runnable r = new Runnable() {
        private final boolean field = x > 1;

        @Override
        public void run() {
          System.out.println((x));
        }
      };
    } else {
      Runnable r = new Runnable() {
        final private boolean field = 1 < x;

        @Override
        public void run() {
          System.out.println(x);
        }
      };
    }
  }
}