// "Replace Optional presence condition with functional style expression" "false"

import java.util.*;

public class Main<T> {
  Optional<Object> foo(Optional<Object> first) {
    // could be replaced in Java-9 with return first.or(() -> "xyz");
    // but the only option in Java-8 is return first.map(Optional::of).orElseGet(() -> Optional.of("xyz")) which is weird
    if (first.isPr<caret>esent()) {
      return first;
    }
    return Optional.of("xyz");
  }
}