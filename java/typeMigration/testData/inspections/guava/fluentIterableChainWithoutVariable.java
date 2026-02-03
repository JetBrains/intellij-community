import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.Iterator;

class Main {
  void mmm() {
    Iterator<String> iterator = m().iterator();
    int i = m1() + 10;
    FluentIterable<String> strings = m2();
  }

  Iterable<String> m() {
    return FluentIterable.from(new ArrayList<String>()).transform(s -> s + s).filter(String::isEmpty);
  }

  int m1() {
    return FluentIterable.from(new ArrayList<String>()).transform(s -> s + s).size();
  }

  FluentIterable<String> m2() {
    return FluentIterable.from(new ArrayList<String>()).transform(s -> s + s).filter(String::isEmpty);
  }

}