// "Collapse loop with stream 'sum()'" "true-preview"
import java.util.List;

public class Main {
  public int sum(List<Integer> list) {
    int sum = 0;
    for(Integer x : li<caret>st) {
      sum += x;
    }
    return sum;
  }
}