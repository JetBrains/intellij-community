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

  public MyItem testMap(Map<CharSequence, MyItem> map, String token) {
    // Cannot create method reference here as MyItem wants a String, but lambda will receive a CharSequence
    MyItem item = map.get(token);
    if(item == nul<caret>l) {
      map.put(token, item = new MyItem(token));
    }
    return item;
  }
}