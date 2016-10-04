// "Replace with toArray" "true"

import java.util.*;

public class Main {
  public String[] testToArray(List<String> data) {
    Set<String> result = new LinkedHashSet<>();
    if(!data.isEmpty()) {
      for (String str : d<caret>ata) {
        if (!str.isEmpty()) {
          result.add(str.trim());
        }
      }
      return result.toArray(new String[result.size()]);
    }
    result.add("None");
    return result.toArray(new String[1]);
  }
}