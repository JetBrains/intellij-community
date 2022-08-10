// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.*;

public class Main<T> {
  Optional<Object> foo(Optional<Object> first) {
    if (first.isPr<caret>esent()) {
      return first;
    }
    return Optional.empty();
  }
}