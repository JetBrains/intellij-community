// "Replace with toArray" "true"

import java.util.*;

public class Test {
  public static String[] test(String[] args) {
    List<String> list = new ArrayList<>();
    for(String s : ar<caret>gs) {
      if(!s.isEmpty())
        list.add(s);
    }
    Set<String> set = new HashSet<>(list);
    String[] array = set.toArray(new String[set.size()]);
    Arrays.sort(array, String.CASE_INSENSITIVE_ORDER);
    return array;
  }
}
