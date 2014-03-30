package pkg;

import java.util.Collection;
import java.util.Comparator;

class Bounds {
  public static <T extends Object & Comparable<? super T>> T max(Collection<? extends T> coll) {
    return null;
  }

  public static <T> T max(Collection<? extends T> coll, Comparator<? super T> comp) {
    return null;
  }
}
