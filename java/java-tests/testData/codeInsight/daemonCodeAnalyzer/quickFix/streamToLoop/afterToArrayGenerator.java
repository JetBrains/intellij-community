// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.*;

public class Main {
  private static <A> A[] toArraySkippingNulls(List<?> list, IntFunction<A[]> generator) {
      List<Object> result = new ArrayList<>();
      for (Object o : list) {
          if (o != null) {
              result.add(o);
          }
      }
      return result.toArray(generator.apply(0));
  }
}