// "Replace with 'Map.Entry.comparingByKey(Comparator.reverseOrder())'" "true-preview"

import java.util.*;

public class Main {
  void sortEntries(List<Map.Entry<String, Integer>> entries) {
    entries.sort(<caret>Map.Entry.comparingByKey().reversed());
  }
}
