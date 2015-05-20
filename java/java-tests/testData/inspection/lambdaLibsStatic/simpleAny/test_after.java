import com.google.common.collect.Iterables;

import java.lang.String;
import java.util.Collections;

class c {
  void m() {
    Collections.emptyList().stream().anyMatch(in -> false);
  }
}