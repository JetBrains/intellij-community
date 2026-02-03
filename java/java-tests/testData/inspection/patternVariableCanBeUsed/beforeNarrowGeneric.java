// "Replace 'c' with pattern variable" "true-preview"
package org.qw;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class PatternCollection {
  public static void is(Object o) {
    if (o instanceof Set) {
      Collection<?> c<caret> = (Collection<?>)o;
      System.out.println(c.size());
    }
  }

  public static void main(String[] args) {
    is(Set.of());
    is(List.of());
  }
}