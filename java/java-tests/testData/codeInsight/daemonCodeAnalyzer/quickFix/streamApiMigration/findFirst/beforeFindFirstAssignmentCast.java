// "Collapse loop with stream 'findFirst()'" "true-preview"

import java.util.List;

public class Main {
  public void testCast(List<Object> data) {
    String found = null;
    for (Object obj : da<caret>ta) {
      if(obj instanceof String) {
        found = (String)obj;
        break;
      }
    }
    System.out.println(found);
  }
}