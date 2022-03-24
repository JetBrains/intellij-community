/*
Size is always zero (map.entrySet(); line#10)
  'map.size == 0' was established from condition (map.isEmpty(); line#9)
 */
import java.util.*;

public class EmptyCollectionSimple {
  void test(Map<String, Integer> map) {
    if (map.isEmpty()) {
      for(var e : <selection>map.entrySet()</selection>) {

      }
    }
  }
}