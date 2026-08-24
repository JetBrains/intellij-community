// "Create method 'transform'" "true"

import java.util.stream.*;

class X {
  
  void x() {
    Stream.of("one", "two")
      .map(this::<caret>transform)
      .forEach(System.out::println);
  }
}