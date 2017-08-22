// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;

public class Test {
  public static void test(List<String> data) {
    List<String> result = new ArrayList<>();
    for(String s : da<caret>ta) {
      if(s.isEmpty()) {
        break;
      }
      result.add(s);
    }
    System.out.println(result);
  }
}
