// "Replace with toArray" "true"

import java.util.*;

public class Main {
  public String[] testToArray(List<String> data) {
    Set<String> result = new HashSet<>();
    for (String str : d<caret>ata) {
      if (!str.isEmpty())
        result.add(str);
    }
    return result.toArray(new String[0]);
  }
}