// "Create method 'transform'" "true"

import java.util.stream.*;

class X {
  
  void x() {
    Stream.of("one", "two")
      .map(this::transform)
      .forEach(System.out::println);
  }

    private Object transform(final String s) {
        return null;
    }
}