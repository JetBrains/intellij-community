// "Apply conversion '.toArray(new java.util.List[0][])'" "true"

import java.util.*;
class GenericArray {
  static List<String>[][] foo() {
    Set<List<String>[]> set = new HashSet<>();
    set.add(new List[]{Arrays.asList("a", "b"), Arrays.asList("c", "d")});
    List<String>[][] arr = set.toArray(new List[0][]);
    return arr;
  }
}