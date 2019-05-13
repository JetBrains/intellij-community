// "Replace with Comparator.comparing" "false"

import java.util.*;

public class Main {
  public <K extends Comparable, V extends Comparable> void sortByValues(Map<K, V> inMap,
                                                                        HashMap<Character, Float> outMap) {

    inMap.entrySet()
      .stream()
      .sorted((it1, it2<caret>) -> it1.getValue().compareTo(it2.getValue()))
      .forEachOrdered(it -> outMap.put((Character) it.getKey(), (Float) it.getValue()));
  }
}
