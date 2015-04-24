import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

class c {
  void m() {
    List<String> l = new ArrayList<String>();
    Iterables.filt<caret>er(l, String.class);
  }
}