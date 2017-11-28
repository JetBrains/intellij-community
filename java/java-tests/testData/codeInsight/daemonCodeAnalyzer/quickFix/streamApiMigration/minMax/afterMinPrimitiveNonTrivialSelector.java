// "Replace with min()" "true"

import java.util.Arrays;

public class Main {
  public void work(int[] ints) {
      int min = Arrays.stream(ints).filter(anInt -> anInt < 10).map(anInt -> anInt + anInt * 2 - 10).filter(anInt -> anInt <= 0).min().orElse(0);
  }
}