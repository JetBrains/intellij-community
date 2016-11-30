// "Replace with 'computeIfAbsent' method call" "true"
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

  public MyItem testMap(Map<String, MyItem> map, String localUser) {
      MyItem item = map.computeIfAbsent(localUser, u -> new MyItem(u, 1));
      return item;
  }
}