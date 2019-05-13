// "Replace with sum()" "false"
import java.util.List;

public class Main {
  public int sum(List<Float> list) {
    int sum = 0;
    for(float x : li<caret>st) {
      sum += x;
    }
    return sum;
  }
}