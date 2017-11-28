// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  public void test(Map<String, String[]> map) {
    List<String> result = new ArrayList<>();
      map.entrySet().stream().filter(entry -> entry.getKey().startsWith("x")).map(Map.Entry::getValue).forEach(arr -> {
          for (String str : arr) {
              result.add(str.trim() + arr.length);
          }
      });
  }
}