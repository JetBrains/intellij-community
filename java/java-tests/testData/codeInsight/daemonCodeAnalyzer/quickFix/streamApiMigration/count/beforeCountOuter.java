// "Collapse loop with stream 'count()'" "true-preview"
import java.util.List;
import java.util.Set;

public class Main {
  public void test(List<Set<String>> nested) {
    int count = 0;
    for(Set<String> element : neste<caret>d) {
      if(element != null) {
        for(String str : element) {
          if(str.startsWith("xyz")) {
            ++count;
          }
        }
      }
    }
  }
}