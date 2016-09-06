// "Replace with sum()" "true"

import java.util.Arrays;

public class Main {
  public double test(String[][] array) {
    double d = 10;
      d += Arrays.stream(array).filter(arr -> arr != null).flatMap(Arrays::stream).filter(a -> a.startsWith("xyz")).mapToDouble(a -> 1.0 / a.length()).sum();
    return d;
  }
}