// "Replace loop with 'Arrays.setAll()' method call" "true"

class Test {

  public static Object[] init(int n, boolean b) {
    Object[] data = new Object[n];
    for (<caret>int j = 0; j < n; j++) {
      data[j] = (j / 2 + n == 0) ? "1" : new Object();
    }
    return data;
  }
}