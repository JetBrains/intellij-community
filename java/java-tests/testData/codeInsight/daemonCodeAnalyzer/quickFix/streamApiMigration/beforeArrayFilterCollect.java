// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.List;

public class Main {
  public void test(Integer[] arr) {
    List<Integer> result = new ArrayList<>();
    for(Integer x : a<caret>rr) {
      if(x > 5) {
        result.add(x);
      }
    }
  }
}