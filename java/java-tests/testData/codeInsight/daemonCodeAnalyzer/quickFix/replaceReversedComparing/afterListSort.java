// "Replace with 'Comparator.comparing(..., Comparator.reverseOrder())'" "true-preview"

import java.util.*;

public class Main {
  void sortEntries(List<Map.Entry<String, Integer>> entries) {
    entries.sort(Comparator.comparing(Map.Entry::getKey, Comparator.reverseOrder()));
  }
}
