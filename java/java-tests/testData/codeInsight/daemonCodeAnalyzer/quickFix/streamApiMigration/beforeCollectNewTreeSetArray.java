// "Replace with toArray" "true"

import java.util.*;

public class Test {
  public static String[] test(String[] args) {
    List<String> list = new ArrayList<>();
    for(String s : ar<caret>gs) {
      if(!s.isEmpty())
        list.add(s);
    }
    Set<String> set = new TreeSet<>(list);
    return set.toArray(new String[0]);
  }
}
