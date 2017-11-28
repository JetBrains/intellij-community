// "Replace with min()" "true"

import java.util.*;

public class Main {
  public void work(int[] ints) {
      int min = Arrays.stream(ints).filter(anInt -> anInt < 10).map(anInt -> anInt + 2343).filter(anInt -> anInt <= 0).min().orElse(0);
  }
}