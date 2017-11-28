// "Replace with collect" "true"
import java.util.HashMap;
import java.util.Map;

public class Main {
  private Map<String, Integer> test(String... list) {
    Map<String, Integer> map = new HashMap<>();
    for(String s : li<caret>st) {
      if(s != null) {
        map.merge(s, 1, (a, b) -> a + b);
      }
    }
    return map;
  }

  public static void main(String[] args) {
    System.out.println(new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs", "dd"));
  }
}
