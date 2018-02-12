// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  public void test(Map<String, String[]> map) {
    List<String> result = new ArrayList<>();
    for(Map.Entry<String, String[]> entry: map.<caret>entrySet())
      if (entry.getKey().startsWith("x")) {
        String[] arr = entry.getValue();
        for (String str : arr) {
          result.add(str.trim()+arr.length);
        }
      }
  }
}