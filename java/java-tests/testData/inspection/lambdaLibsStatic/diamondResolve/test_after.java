import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

import java.lang.Iterable;
import java.util.HashMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.stream.Collectors;

class c {
  void m() {
    Iterable<HashMap> l = new ArrayList<HashMap>().stream().filter(op -> false).collect(Collectors.toList());
  }
}