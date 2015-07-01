import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

import java.lang.Iterable;
import java.util.HashMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.stream.Collectors;

class c {
  void m() {
    Iterable<HashMap> l = Collections.<HashMap>emptyList().stream().filter(op -> false).collect(Collectors.toList());
  }
}