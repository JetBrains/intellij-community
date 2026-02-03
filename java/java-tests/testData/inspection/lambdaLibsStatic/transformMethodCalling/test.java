import com.google.common.collect.Iterables;
import com.google.common.base.Function;

import java.lang.Iterable;
import java.lang.String;
import java.util.ArrayList;
import java.util.Collections;

class c {
  void m() {
    Iterable<String> trnsfrmd = Iterables.transf<caret>orm(new ArrayList<>(), getFunction());
  }

  Function<String, String> getFunction() {
    return new Function<String, String>() {
      String apply(String s) {
        return s.toString().toLowerCase().replace('c', 'h');
      }
    }
  }
}