// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.function.LongSupplier;

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
      LongSupplier sup = () -> count*2;
      long result = sup.get();
      System.out.println(result);
    }
  }
}