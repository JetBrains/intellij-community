import com.google.common.collect.Iterables;

import java.lang.String;
import java.util.Collections;

class c {
  void m() {
    Iterables.an<caret>y(Collections.emptyList(), in -> false);
  }
}