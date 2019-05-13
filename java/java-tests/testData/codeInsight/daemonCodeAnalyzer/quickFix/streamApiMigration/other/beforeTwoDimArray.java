// "Replace with sum()" "true"

public class Matrix {

  public double trace(final double[][] a) {
    double sum = 0;

    f<caret>or (int i = 0; i < a.length; i++) {
      sum += a[i][i];
    }

    return sum;
  }
}
