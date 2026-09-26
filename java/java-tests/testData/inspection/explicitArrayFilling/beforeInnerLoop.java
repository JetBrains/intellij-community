// "Remove 'for' statement" "true"

public class Test {

  public static int[] init(int n) {
    int[] data = new int[n];
    int i = 0;
    while (i < 3) {
      for (<caret>int j = 0; j < data.length; j++) {
        data[j] = 0;
      }
      i++;
    }
    return data;
  }
}