// "Replace with max()" "true"

import java.util.*;

class Main {
  public void work(long[] ints) {
      long max = Arrays.stream(ints).filter(anInt -> anInt < 10).max().orElse(Long.MIN_VALUE);
  }
}