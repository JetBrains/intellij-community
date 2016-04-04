import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main17 {

  interface A {
    FluentIterable<String> getIterable();
  }

  class B implements A {
    @Override
    public FluentIter<caret>able<String> getIterable() {
      return FluentIterable.from(new ArrayList<String>()).transform(new Function<String, String>() {
        @Override
        public String apply(String s) {
          return s.intern();
        }
      });
    }
  }

  static void m(A a) {
    int s = a.getIterable().transform(s1 -> s1).size();
  }

  static void m2(B b) {
    int s = b.getIterable().size();
  }
}
