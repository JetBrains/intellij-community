// "Replace with max()" "true"

import java.util.*;

public class Main {
  public void work(int[] ints) {
    int max = Integer.MIN_VALUE;
    for<caret> (int i : ints) {
      if(i < max) continue;
      max = i;
    }
  }
}