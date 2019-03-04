import java.util.*;
import java.util.function.*;

class X {
  Optional<Optional<?>> opt1;
  void test(int i) {
    Optional<?> res = ((Supplier<Optional<?>>)() -> {
      try {
        if (opt1.get() == opt1) {
          return opt1;
        }
      }
      finally {
        return Optional.empty();
      }
    }).get();
    if(<warning descr="Condition 'res.isPresent()' is always 'false'">res.isPresent()</warning>) {}
  }
}
