// "Collapse loop with stream 'sum()'" "true-preview"

public class Main {
  public void test(long[] list) {
    int sum = 0;
    for(long x : li<caret>st) {
      sum+=x;
    }
  }
}