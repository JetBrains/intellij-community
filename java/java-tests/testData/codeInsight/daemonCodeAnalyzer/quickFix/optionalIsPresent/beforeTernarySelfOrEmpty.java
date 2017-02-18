// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main<T> {
  Optional<Object> foo(Optional<Object> first) {
    Optional<Object> o = !first.isPrese<caret>nt() ? Optional.empty() : first;
    return o;
  }
}