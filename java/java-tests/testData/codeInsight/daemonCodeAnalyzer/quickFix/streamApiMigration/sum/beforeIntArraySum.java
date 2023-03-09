// "Collapse loop with stream 'sum()'" "true-preview"
public class Main {
  public int sum(int[] array) {
    int sum = 0;
    for(int x : arr<caret>ay) {
      sum += x;
    }
    return sum;
  }
}