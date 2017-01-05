// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.*;

public class Main {
  private static <A> A[] toArraySkippingNulls(List<?> list, IntFunction<A[]> generator) {
    return list.stream().filter(Objects::nonNull).toAr<caret>ray(generator);
  }
}