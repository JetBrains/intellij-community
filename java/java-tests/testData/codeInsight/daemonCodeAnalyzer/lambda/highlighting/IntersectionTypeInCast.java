import java.io.Serializable;
import java.util.*;

class Test {
  public static <K extends Comparable<? super K>, V> Comparator<Map.Entry<K, V>> foo() {
    return (Comparator<Map.Entry<K, V>> & Serializable)(c1, c2) -> c1.getKey().compareTo(c2.getKey());
  }
}
