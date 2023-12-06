// "Replace loop with 'Arrays.fill()' method call" "false"
package pack;

public class TableWrapper {

  public static double[][] diag(final int n, final double value) {
    final double[][] test = new double[n][n];
    for (<caret>int i = 0; i < n; ++i)   test[i][i] = value;
    return test;
  }}