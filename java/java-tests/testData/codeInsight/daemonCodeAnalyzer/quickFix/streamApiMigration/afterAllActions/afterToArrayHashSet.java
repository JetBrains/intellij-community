// "Collapse loop with stream 'toArray()'" "true-preview"

import java.util.*;

public class Main {
  public String[] testToArray(List<String> data) {
      return data.stream().filter(str -> !str.isEmpty()).distinct().toArray(String[]::new);
  }
}