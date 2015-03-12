import com.google.common.collect.Iterables;

import java.lang.String;
import java.util.Collections;

class c {
  void m() {
    Iterables.al<caret>l(Collections.emptyList(), in -> false);
  }
}