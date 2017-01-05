// "Replace with collect" "true"
import java.util.HashMap;
import java.util.Map;

public class Main {
  private Map<String, Integer> test(String... list) {
    HashMap<String, Integer> map = new HashMap<>();
    for(String s : lis<caret>t) {
      if(s != null) {
        map.put(s, 1);
      }
    }
    return map;
  }

  public static void main(String[] args) {
    System.out.println(new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs", "dd"));
  }
}
