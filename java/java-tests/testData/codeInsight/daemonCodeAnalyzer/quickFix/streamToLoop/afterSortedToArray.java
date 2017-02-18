// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
  public List<String> testSorted(List<String> list) {
      List<String> result = new ArrayList<>();
      for (String s : list) {
          result.add(s);
      }
      result.sort(null);
      return result.toArray(new String[0]);
  }
}