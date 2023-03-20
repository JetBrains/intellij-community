// "Collapse loop with stream 'collect()'" "true-preview"
import java.util.*;

public class Main {
  private Map<Integer, List<String>> test(String... list) {
    Map<Integer, List<String>> map = new HashMap<>();
    for(String s : li<caret>st) {
      if(s != null) {
        map.computeIfAbsent(s.length(), k -> new ArrayList<>()).add(s);
      }
    }
    return map;
  }

  public static void main(String[] args) {
    System.out.println(new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs", "dd"));
  }
}
