// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Main {
  public void test(List<Set<String>> nested) {
    List<String> result = new ArrayList<>();
    for (Set<String> element : nes<caret>ted) { // 1
      if (element /*non-equal*/!= null) {
        for (String str : element) {
          if (str./*startswith*/startsWith("xyz")) {
            String target = str.trim(/*empty*/);
            result.add(/*target is here*/target);
          } // 2
        } // 3
      } // 4
    }
  }
}