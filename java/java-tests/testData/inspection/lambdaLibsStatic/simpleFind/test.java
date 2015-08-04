import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

import java.lang.String;
import java.util.Collections;

class c {
  void m() {
    Iterables.f<caret>ind(Collections.emptyList(), new Predicate<String>() {
      @Override
      public boolean apply(String input) {
        return true;
      }
    });
  }
}