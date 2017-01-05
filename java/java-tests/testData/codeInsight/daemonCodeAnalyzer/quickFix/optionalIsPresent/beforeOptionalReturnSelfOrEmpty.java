// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main<T> {
  Optional<Object> foo(Optional<Object> first) {
    if (first.isPr<caret>esent()) {
      return first;
    }
    return Optional.empty();
  }
}