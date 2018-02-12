// "Replace with toArray" "true"

import java.util.*;

public class Main {
  public String[][] testToArray(List<String> data) {
      return data.stream().filter(str -> !str.isEmpty()).map(str -> new String[]{str}).toArray(String[][]::new);
  }
}