// "Replace with sum()" "true"
import java.util.List;

public class Main {
  public int sum(List<Integer> list) {
      int sum = list.stream().mapToInt(x -> x).sum();
      return sum;
  }
}