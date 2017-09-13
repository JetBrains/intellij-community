// "Replace with collect" "true"
import java.util.*;

public class Main {
  Map<Integer, List<String>> test(List<String> list) {
    Map<Integer, List<String>> map = new HashMap<>();
    for<caret> (String s : list) {
      List<String> strings = map.computeIfAbsent(s.length(), k -> new ArrayList<>());
      strings.add(s);
    }
    return map;
  }

  public static void main(String[] args) {
    System.out.println(new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs", "dd"));
  }
}
