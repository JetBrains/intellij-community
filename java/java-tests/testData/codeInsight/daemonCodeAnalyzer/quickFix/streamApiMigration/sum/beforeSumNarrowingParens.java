// "Replace with sum()" "true-preview"

public class Main {
  public void test(double[] list) {
    int sum = 0;
    for(double x : li<caret>st) {
      sum+=x * x;
    }
  }
}