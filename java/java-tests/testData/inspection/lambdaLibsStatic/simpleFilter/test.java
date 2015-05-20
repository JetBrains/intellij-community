import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

import java.lang.String;
import java.util.Collections;

class c {
  void m() {
    Iterables.fil<caret>ter(Collections.emptyList(), new Predicate<Object>() {
      @Override
      public boolean apply(Object input) {
        return true;
      }
    });
  }
}