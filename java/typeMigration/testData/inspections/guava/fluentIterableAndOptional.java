import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main18 {

  class A {
    private String i = "12";

    Optional<String> getOpt() {
      return Optional.fromNullable(i);
    }
  }

  class B {
    FluentIt<caret>erable<String> getFIterable() {
      return FluentIterable.from(new ArrayList<String>()).filter(String.class);
    }
  }

  void m(A a, B b) {
    String sss = b.getFIterable().transform(s -> s).first().or(a.getOpt()).get();
  }
}
