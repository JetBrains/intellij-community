// "Apply conversion '.toArray(new java.util.List[0])'" "true"

import java.util.*;
class Generic {
  List<String>[] foo() {
    Set<List<String>> set = new HashSet<>();
    List<String>[] arr = set.toArray(new List[0]);
    return arr;
  }
}