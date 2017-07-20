// "Replace with collect" "true"
import java.util.*;

public class Main {
  private Map<Integer, ArrayList<String>> test(String... list) {
    Map<Integer, ArrayList<String>> map = new HashMap<>();
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
