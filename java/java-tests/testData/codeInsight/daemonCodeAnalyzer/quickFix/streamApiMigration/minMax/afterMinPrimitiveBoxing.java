// "Replace with min()" "true"

import java.util.*;

public class Main {
  public void work(Integer[] ints) {
      int min = Arrays.stream(ints).filter(anInt -> anInt < 10).mapToInt(anInt -> anInt).filter(anInt -> anInt <= 0).min().orElse(0);
  }
}