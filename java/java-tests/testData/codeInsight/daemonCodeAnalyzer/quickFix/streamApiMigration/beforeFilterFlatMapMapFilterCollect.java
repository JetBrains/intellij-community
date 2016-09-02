// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.List;

public class Main {
  public void test(List<String[]> list) {
    List<String> result = new ArrayList<>();
    for(String[] arr : li<caret>st) {
      if(arr.length > 2) {
        for(String str : arr) {
          String trimmed = str.trim();
          if(!trimmed.isEmpty()) {
            result.add(trimmed);
          }
        }
      }
    }
  }
}