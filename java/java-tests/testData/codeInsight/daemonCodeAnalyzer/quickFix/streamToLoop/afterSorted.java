// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
  public List<String> testSorted(List<String> list) {
      List<String> toSort = new ArrayList<>();
      for (String s: list) {
          if (s != null) {
              toSort.add(s);
          }
      }
      toSort.sort(null);
      List<String> result = new ArrayList<>();
      Set<String> uniqueValues = new HashSet<>();
      for (String s: toSort) {
          String trim = s.trim();
          if (uniqueValues.add(trim)) {
              result.add(trim);
          }
      }
      return result;
  }

  public List<String> testSortedComparator(List<String> list) {
      List<String> result = new ArrayList<>();
      for (String s: list) {
          result.add(s);
      }
      result.sort(String.CASE_INSENSITIVE_ORDER);
      return result;
  }

  public List<String> testSortedToArray(List<String> list) {
      List<String> result = new ArrayList<>();
      for (String s: list) {
          result.add(s);
      }
      result.sort(null);
      return result.toArray(new String[0]);
  }
}