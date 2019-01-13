// "Replace with sum()" "true"

import java.util.stream.IntStream;

public class Matrix {

  public double trace(final double[][] a) {
    double sum = IntStream.range(0, a.length).mapToDouble(i -> a[i][i]).sum();

      return sum;
  }
}
