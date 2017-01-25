// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.function.LongSupplier;

public class Main {
  private static void test(List<String> list) {
      // and filter!
      long count1 = 0L;
      for (String s : list) {
          if (!s/* comment */.isEmpty()) {
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