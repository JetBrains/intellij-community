import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    FluentIterable<String> i<caret>t = FluentIterable.from(strings);

    int i = it.transformAndConcat(new Function<String, Iterable<String>>() {
      @Override
      public Iterable<String> apply(String o) {
        if ('a' > 2) {
          return getIterable();
        } else if ('c' < 123) {
          ArrayList<String> strings1 = new ArrayList<>();
          strings1.add(o);
          return strings1;
        }
        return null;
      }
    }).size();

  }

  Iterable<String> getIterable() {
    return null;
  }
}