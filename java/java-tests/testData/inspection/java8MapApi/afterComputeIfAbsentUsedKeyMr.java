// "Replace with 'computeIfAbsent' method call" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  static class MyItem {
    String k;

    MyItem(String k) {
      this.k = k;
    }
  }

  public MyItem testMap(Map<String, MyItem> map, String token) {
      MyItem item = map.computeIfAbsent(token, MyItem::new);
      return item;
  }
}