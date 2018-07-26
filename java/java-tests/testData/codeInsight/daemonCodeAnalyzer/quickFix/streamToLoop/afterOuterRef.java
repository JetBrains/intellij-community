// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;

public class Test extends ArrayList<String> {
  void test() {
    new Runnable() {
      @Override
      public void run() {
          int sum = 0;
          for (String s : Test.this) {
              int length = s.length();
              sum += length;
          }
          System.out.println(sum);
      }
    };
  }
}
