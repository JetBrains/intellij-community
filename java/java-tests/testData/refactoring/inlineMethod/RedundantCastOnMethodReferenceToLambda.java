
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

import static java.util.function.Predicate.isEqual;

class InlineRef {
  Optional<? extends Descriptor> findEmpty() {
    Set<? extends Descriptor> children = new HashSet<>();
    return children
      .stream()
      .filter(where(InlineRef::get<caret>Name, isEqual("")))
      .findAny();
  }


  static <T, V> Predicate<T> where(Function<T, V> function, Predicate<? super V> predicate) {
    return input -> predicate.test(function.apply(input));
  }

  static String getName(Descriptor desc) {
    return "name";
  }
}

class Descriptor { }