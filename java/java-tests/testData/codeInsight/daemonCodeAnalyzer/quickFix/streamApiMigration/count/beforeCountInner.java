// "Replace with count()" "true"
import java.util.List;
import java.util.Set;

public class Main {
  public void test(List<Set<String>> nested) {
    int count = 0;
    for(Set<String> element : nested) {
      if(element != null) {
        for(String str : eleme<caret>nt) {
          if(str.startsWith("xyz")) {
            count++;
          }
        }
      }
    }
  }
}