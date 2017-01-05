// "Replace with sum()" "true"
import java.util.List;

public class Main {
  public int sum(List<Integer> list) {
    int sum = 0;
    for(int x : li<caret>st) {
      sum += x;
    }
    return sum;
  }
}