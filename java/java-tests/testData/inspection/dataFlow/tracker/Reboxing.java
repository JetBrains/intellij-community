/*
Value is always false (x == null; line#11)
  'x' was assigned (=; line#10)
    Primitive value was boxed ("0".equals(key) ? -1 : m.get(key); line#10)
 */
import java.util.*;

public class Reboxing {
  void foo(Map<String, Integer> m, String key) {
    Integer x = "0".equals(key) ? -1 : m.get(key);
    if (<selection>x == null</selection>) {
      System.out.println("hello");
    }
  }
}