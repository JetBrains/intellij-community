import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.List;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    FluentIterable<String> i<caret>t = FluentIterable.from(strings);

    int i = it.transformAndConcat(getFunction()).size();
  }

  Function<String, List<String>> getFunction() {
    return null;
  }

}