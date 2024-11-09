/*
Value is always false (x == null; line#13)
  'x' was assigned (=; line#12)
    One of the following happens:
      Primitive value was boxed ("0".equals(key) ? -1 : m.get(key); line#12)
      or expression cannot be null as it's a value of primitive type 'int' (-1; line#12)
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