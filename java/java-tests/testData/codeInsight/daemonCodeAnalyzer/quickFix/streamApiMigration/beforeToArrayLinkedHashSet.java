// "Replace with toArray" "true"

import java.util.*;

public class Main {
  public CharSequence[] testToArray(List<String> data) {
    Set<String> result = new LinkedHashSet<>();
    for (String str : d<caret>ata) {
      if (!str.isEmpty())
        result.add(str);
    }
    return result.toArray(new CharSequence[result.size()]);
  }
}