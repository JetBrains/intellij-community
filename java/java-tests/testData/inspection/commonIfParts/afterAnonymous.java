// "Collapse 'if' statement" "true"

import java.util.List;
import java.util.Map;

public class Main {

  void test(int x) {
      Runnable r = new Runnable() {
        private final boolean field = x > 1;

        @Override
        public void run() {
          System.out.println((x));
        }
      };
  }
}