// "Replace with collect" "true"
import java.util.ArrayList;
  import java.util.List;

public class Main {
  public List<Integer> test(int[][] arr) {
    List<Integer> result = new ArrayList<>();
    for(int[] subArr : a<caret>rr) {
      if(subArr != null) {
        for(int str : subArr) {
          result.add(str);
        }
      }
    }
    return result;
  }
}