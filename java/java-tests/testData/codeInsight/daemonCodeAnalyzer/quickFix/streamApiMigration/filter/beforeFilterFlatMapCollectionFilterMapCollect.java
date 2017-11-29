// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Main {
  public void test(List<Set<String>> nested) {
    List<String> result = new ArrayList<>();
    for (Set<String> element : nes<caret>ted) {
      if (element != null) {
        for (String str : element) {
          if (str.startsWith("xyz")) {
            String target = str.trim();
            result.add(target);
          }
        }
      }
    }
  }
}