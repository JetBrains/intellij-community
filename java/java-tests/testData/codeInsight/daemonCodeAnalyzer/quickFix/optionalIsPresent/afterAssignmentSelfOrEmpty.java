// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.*;

public class Main<T> {
  Optional<Object> foo(Optional<Object> first) {
    Optional<Object> o;
      o = first;
    return o;
  }
}