// "Replace loop with 'Arrays.fill()' method call" "true"

public class Test {

  public static int[] init(int n) {
    int[] data = new int[n];
    int i = 0;
    while (i < 3) {
      for (<caret>int j = 0; j < data.length; j++) {
        data[j] = 0;
      }
      if (i < 2) data[n - 1] = 6;
      i++;
    }
    return data;
  }
}