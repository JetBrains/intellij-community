// "Collapse loop with stream 'collect()'" "true-preview"
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
  private Map<String, Integer> test(String... list) {
    Map<String, Integer> map = new LinkedHashMap<>();
    for(String s : l<caret>ist) {
      if(s != null) {
        map.putIfAbsent(s, 1);
      }
    }
    return map;
  }

  public static void main(String[] args) {
    System.out.println(new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs", "dd"));
  }
}
