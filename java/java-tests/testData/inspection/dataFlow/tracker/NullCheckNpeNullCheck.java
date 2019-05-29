/*
Value is always true (map != null; line#18)
  'map' was dereferenced (map; line#14)
 */

import java.util.Map;

public class ExplainMe {
  void checkTheMap(Map<String, Integer> map) {
    if (map == null) {
      System.out.println("that's null");
    }

    if (map.get("ONE") == null) {
      System.out.println("Okay");
    }

    if (<selection>map != null</selection>) {
      System.out.println("not null");
    }
  }
}