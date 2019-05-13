import com.google.common.base.Predicate;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.List;

class A {
  int m1() {
    String[] strings = new String[10];
    FluentIterable<String> i<caret>t = FluentIterable.of(strings).transform(s -> s + s);

    return it.skip(10).size();
  }
}