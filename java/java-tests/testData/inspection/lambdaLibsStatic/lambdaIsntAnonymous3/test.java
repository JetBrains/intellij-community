import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import java.lang.String;
import java.util.Collections;

class c {
  void m() {
    Iterables.al<caret>l(Collections.<String>emptyList(), getPredicate(100));
  }

  public Predicate<String> getPredicate(final int param) {
    return Predicates.not(new Predicate<String>() {
      @Override
      public boolean apply(String input) {
        System.out.println("lambda param " + param);
        return false;
      }
    });
  }
}