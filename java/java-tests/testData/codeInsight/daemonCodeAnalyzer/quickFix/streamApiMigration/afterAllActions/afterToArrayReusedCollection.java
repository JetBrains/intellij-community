// "Replace with toArray" "true"

import java.util.*;

public class Main {
  public String[] testToArray(List<String> data) {
    Set<String> result = new LinkedHashSet<>();
    if(!data.isEmpty()) {
        return data.stream().filter(str -> !str.isEmpty()).map(String::trim).distinct().toArray(String[]::new);
    }
    result.add("None");
    return result.toArray(new String[1]);
  }
}