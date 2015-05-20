import com.google.common.collect.Iterables;

import java.lang.String;
import java.util.List;
import java.util.ArrayList;

class c {
  void m() {
    List<String> l = new ArrayList<>();
    Iterable<Boolean> transform = Iterables.tr<caret>ansform(l, new Function<String, Boolean>() {
      @Override
      public Boolean apply(String input) {
        return input.isEmpty();
      }
    });
  }
}