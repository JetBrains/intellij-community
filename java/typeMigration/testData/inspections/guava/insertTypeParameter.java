import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.Collections;

class A {
  void c() {
    FluentIterable<String> i<caret>t = FluentIterable.from(new ArrayList<>());
    int i = it.transformAndConcat(input -> Collections.emptyList()).size();
  }
}