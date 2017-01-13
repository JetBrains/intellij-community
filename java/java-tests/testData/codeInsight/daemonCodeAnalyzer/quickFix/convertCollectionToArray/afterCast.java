// "Apply conversion '.toArray(new java.lang.Integer[0])'" "true"

import java.util.*;
class Cast {
  Integer[] foo() {
    Iterable<Integer> c = Arrays.asList(1, 2);
    Integer[] arr = ((Collection<Integer>) c).toArray(new Integer[0]);
    return arr;
  }
}