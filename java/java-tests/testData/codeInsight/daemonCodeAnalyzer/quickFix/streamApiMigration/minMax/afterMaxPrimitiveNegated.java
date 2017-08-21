// "Replace with max()" "true"

import java.util.*;

public class Main {
  public void work(int[] ints) {
      int max = Arrays.stream(ints).max().orElse(Integer.MIN_VALUE);
  }
}