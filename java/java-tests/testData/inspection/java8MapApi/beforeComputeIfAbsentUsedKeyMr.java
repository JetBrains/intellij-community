// "Replace with 'computeIfAbsent()' call" "true"
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
    MyItem item = map.get(token);
    if<caret>(item == null) {
      map.put(token, item = new MyItem(token));
    }
    return item;
  }
}