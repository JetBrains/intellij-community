import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.Collections;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    FluentIterable<String> i<caret>t = FluentIterable.from(strings);

    int i = it.transformAndConcat(input -> Collections.emptyList()).size();

  }
}