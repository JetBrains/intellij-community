// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.List;

public class Main {
  public List<String> test(String[][] arr) {
    List<String> result = new ArrayList<>();
    for(String[] subArr : a<caret>rr) {
      if(subArr != null) {
        for(String str : subArr) {
          result.add(str);
        }
      }
    }
    return result;
  }
}