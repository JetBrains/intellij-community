// "Collapse loop with stream 'max()'" "false"

import java.util.*;

class X {
  public final class Abc {
    public static final int A = Abc.B;
    public static final int B = A;

    int test(List<Integer> list) {
      int max = B;
      for (Integer s : <caret>list) {
        if (s > max) {
          max = s;
        }
      }
      return max;
    }
  }
}