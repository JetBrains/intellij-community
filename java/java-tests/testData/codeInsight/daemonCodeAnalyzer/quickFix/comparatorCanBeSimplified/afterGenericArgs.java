// "Replace with 'Entry.comparingByKey()'" "true"

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class X {
  private static List<Map.Entry<String, Integer>> sortFrequencies(Map<String, Integer> freq) {
    return freq.entrySet().stream()
      .sorted(Map.Entry.<String, Integer>comparingByKey().thenComparing(Map.Entry::getValue))
      .collect(Collectors.toList());
  }
}