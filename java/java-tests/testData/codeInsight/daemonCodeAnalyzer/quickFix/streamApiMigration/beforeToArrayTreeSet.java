// "Replace with toArray" "true"

import java.util.*;

public class Main {
  public Object[] testToArray(List<String> data) {
    Set<String> result = new TreeSet<>();
    for (String str : d<caret>ata) {
      if (!str.isEmpty())
        result.add(str);
    }
    return result.toArray(new String[result.size()]);
  }
}