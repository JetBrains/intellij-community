import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main {
  Fluent<caret>Iterable<String> m1() {
    return FluentIterable.from(new ArrayList<String>()).transform(s -> s + s);
  }

  Optional<String> m2() {
    return m1().firstMatch(new Predicate<String>() {
      @Override
      public boolean apply(String s) {
        return s.indexOf('s') == 123;
      }
    });
  }

  void m3() {
    String target = m2().orNull();
  }
}