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
      MyItem item = map.computeIfAbsent(token, t -> new MyItem(t, 1));
      return item;
  }
}