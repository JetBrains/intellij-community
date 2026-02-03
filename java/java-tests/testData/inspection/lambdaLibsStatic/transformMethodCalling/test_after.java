import com.google.common.collect.Iterables;
import com.google.common.base.Function;

import java.lang.Iterable;
import java.lang.String;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

class c {
  void m() {
    Iterable<String> trnsfrmd = new ArrayList<String>().stream().map(getFunction()::apply).collect(Collectors.toList());
  }

  Function<String, String> getFunction() {
    return new Function<String, String>() {
      String apply(String s) {
        return s.toString().toLowerCase().replace('c', 'h');
      }
    }
  }
}