// "Apply conversion '.toArray(new int[0][])'" "true"

import java.util.*;
class Array {
  static int[][] foo() {
    Set<int[]> set = new HashSet<>();
    set.add(new int[]{1, 2});
    int[][] arr <caret>= set;
    return arr;
  }
}