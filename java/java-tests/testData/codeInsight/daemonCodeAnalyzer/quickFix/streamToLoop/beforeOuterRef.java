// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;

public class Test extends ArrayList<String> {
  void test() {
    new Runnable() {
      @Override
      public void run() {
        System.out.println(stream().mapToInt(String::length).s<caret>um());
      }
    };
  }
}
