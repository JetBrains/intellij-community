// "Collapse loop with stream 'toArray()'" "true-preview"

import java.util.ArrayList;
import java.util.List;

public class Main {
  public Object[] testToArray(List<String> data) {
    List<String> result = new ArrayList<>();
    for (String str : d<caret>ata) {
      if (!str.isEmpty())
        result.add(str);
    }
    return result.toArray();
  }
}