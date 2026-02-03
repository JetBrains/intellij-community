import java.util.HashMap;
import java.util.Map;

class CastNeeded2 {

  void m() {
    HashMap<String, String> map2 = new HashMap<>();
    for (Map.Entry<String, String> entry : map2.entrySet()) {
      System.out.println((Number) (Object) entry.getKey());
      System.out.println(entry.getValue());
    }
  }
}