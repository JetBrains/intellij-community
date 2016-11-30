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

  public MyItem testMap(Map<String, MyItem> map, String ___1) {
      MyItem item = map.computeIfAbsent(___1, k -> new MyItem(k, 1));
      return item;
  }
}