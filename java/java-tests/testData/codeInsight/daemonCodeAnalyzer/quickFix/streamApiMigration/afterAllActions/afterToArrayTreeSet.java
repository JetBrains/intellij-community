// "Collapse loop with stream 'toArray()'" "true-preview"

import java.util.*;

public class Main {
  public Object[] testToArray(List<String> data) {
      return data.stream().filter(str -> !str.isEmpty()).distinct().sorted().toArray(String[]::new);
  }
}