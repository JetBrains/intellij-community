import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

import java.lang.Iterable;
import java.util.HashMap;
import java.util.Collections;
import java.util.ArrayList;

class c {
  void m() {
    Iterable<HashMap> l = Iterables.fil<caret>ter(new ArrayList<>(), op -> false);
  }
}