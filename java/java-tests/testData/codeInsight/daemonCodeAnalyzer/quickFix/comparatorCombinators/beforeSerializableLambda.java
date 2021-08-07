// "Replace with 'Comparator.comparing'" "false"
import java.io.Serializable;
import java.util.Comparator;

interface Entry<K> {
  static <K extends Comparable<? super K>> Comparator<Entry<K>> comparingByKey() {
    return (Comparator<Entry<K>> & Serializable)
      (c1, c2) -> c1.<caret>getKey().compareTo(c2.getKey()); // hover here
  }

  K getKey();
}