// "Apply conversion '.toArray(new int[0])'" "false"

import java.util.*;
class Primitive {
  int[] foo() {
    Collection<Integer> c = Arrays.asList(1, 2);
    int[] arr<caret> = c;
    return arr;
  }
}