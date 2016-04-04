import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.stream.Stream;

public class Main18 {

  class A {
    private String i = "12";

    Optional<String> getOpt() {
      return Optional.fromNullable(i);
    }
  }

  class B {
    Stream<String> getFIterable() {
      return new ArrayList<String>().stream().filter(String.class::isInstance);
    }
  }

  void m(A a, B b) {
    String sss = java.util.Optional.ofNullable(b.getFIterable().map(s -> s).findFirst().orElseGet(a.getOpt()::get)).get();
  }
}
