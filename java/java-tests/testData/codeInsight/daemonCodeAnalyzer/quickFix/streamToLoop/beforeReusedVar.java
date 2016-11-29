// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  private static void test(List<String> list) {
    long count = list.stream().filter(s -> !s.isEmpty()).co<caret>unt();
    if(count > 10) {
      long result = count*2;
      System.out.println(result);
    }
  }
}