import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

import java.lang.String;
import java.util.Collections;

class c {
  void m() {
    Collections.<String>emptyList().stream().filter(input -> true).findFirst().get();
  }
}