import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    FluentIterable<String> i<caret>t = FluentIterable.from(strings);

    int i = it.filter(String::isEmpty).filter(String.class).size();
  }
}