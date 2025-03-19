// "Replace 'c' with pattern variable" "true-preview"
package pkg;

import java.util.*;

public class ComplexCast {
  public static void is(Map<String, Integer> o) {
    if (o instanceof AbstractMap<String, Integer> c) {
        final Object o1 = c.get(null);
      System.out.println(c.size());
    }
  }
}

