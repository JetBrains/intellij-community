// "Replace with toArray" "true"

import java.util.ArrayList;
import java.util.List;

public class Main {
  public Object[] testToArray(List<String> data) {
    Object[] arr;
    List<String> result = new ArrayList<>();
    for (String str : d<caret>ata) {
      if (!str.isEmpty())
        result.add(str);
    }
    arr = result.toArray();
    System.out.println(Arrays.toString(arr));
    return arr;
  }
}