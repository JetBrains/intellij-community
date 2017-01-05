// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.function.LongSupplier;

public class Main {
  private static void test(List<String> list) {
    long count = list.stream(). // and filter!
      filter(s -> !s/* comment */.isEmpty()).co<caret>unt();
    if(count > 10) {
      LongSupplier sup = () -> count*2;
      long result = sup.get();
      System.out.println(result);
    }
  }
}