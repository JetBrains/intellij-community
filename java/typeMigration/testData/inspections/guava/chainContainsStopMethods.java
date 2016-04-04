import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.List;

class A {
  int m1() {
    List<String> strings = new ArrayList<>();
    return FluentIterable.fro<caret>m(strings).transform(s -> s + s).uniqueIndex(null).size();
  }
}