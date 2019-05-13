// "Replace with 'computeIfAbsent' method call" "INFORMATION"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  static class MyItem {
    String k;
    int i;

    MyItem(String k, int i) {
      this.k = k;
      this.i = i;
    }
  }

  public MyItem testMap(Map<String, MyItem> map, String token) {
    MyItem item = map.get(token);
    if(item == nul<caret>l) {
      map.put(token, item = new MyItem(token, 1));
    }
    return item;
  }
}