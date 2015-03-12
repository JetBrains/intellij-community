import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

import java.lang.String;
import java.util.Collections;

class c {
  void m() {
    Collections.emptyList().stream().allMatch(getPredicate(100));
  }

  public java.util.function.Predicate<String> getPredicate(final int param) {
    return new java.util.function.Predicate<String>() {
      @Override
      public boolean test(String input) {
        System.out.println("lambda param " + param);
        return false;
      }
    };
  }
}