// "Replace with addAll" "true"
import java.util.ArrayList;
import java.util.List;

public class Main {
  public void test(Integer[] arr) {
    List<Integer> result = new ArrayList<>();
    result.add(1);
    for(Integer i : ar<caret>r)
      result.add(i);
  }
}