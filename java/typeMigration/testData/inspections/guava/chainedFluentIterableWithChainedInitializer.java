import com.google.common.base.Predicate;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.List;

class A {
  int m1() {
    ArrayList<String> strings = new ArrayList<String>();
    FluentIterable<String> i<caret>t = FluentIterable.from(strings).transform(s -> s + s);

    return it.skip(10).size();
  }
}