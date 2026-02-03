import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

import java.lang.String;
import java.util.Collections;
import java.util.stream.Collectors;

class c {
  void m() {
    Collections.emptyList().stream().filter(input -> true).collect(Collectors.toList());
  }
}