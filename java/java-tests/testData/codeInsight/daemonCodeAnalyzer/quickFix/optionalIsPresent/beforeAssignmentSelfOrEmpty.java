// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main<T> {
  Optional<Object> foo(Optional<Object> first) {
    Optional<Object> o;
    if (first.isPrese<caret>nt())
      o = first;
    else
      o = Optional.empty();
    return o;
  }
}