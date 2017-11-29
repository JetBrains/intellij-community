// "Replace with collect" "true"
import java.util.*;

public class Main {
  private Map<Integer, List<String>> test(String... list) {
    Map<Integer, List<String>> map = new HashMap<>();
    for(String s : li<caret>st) {
      if(s != null) {
        List<String> tmp = map.get(s.length());
        if(tmp == null) map.put(s.length(), tmp = new ArrayList<>());
        tmp.add(s);
      }
    }
    return map;
  }

  public static void main(String[] args) {
    System.out.println(new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs", "dd"));
  }
}
