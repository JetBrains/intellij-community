// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  private static void test(List<String> list) {
      long count1 = 0;
      for (String s : list) {
          if (!s.isEmpty()) {
              count1++;
          }
      }
      long count = count1;
    if(count > 10) {
      long result = count*2;
      System.out.println(result);
    }
  }
}