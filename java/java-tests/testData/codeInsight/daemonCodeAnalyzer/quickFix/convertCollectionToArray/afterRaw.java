// "Apply conversion '.toArray()'" "true-preview"

import java.util.*;
class Raw {
  Object[] foo() {
    Set set = new TreeSet();
    Object[] arr = set.toArray();
    return arr;
  }
}