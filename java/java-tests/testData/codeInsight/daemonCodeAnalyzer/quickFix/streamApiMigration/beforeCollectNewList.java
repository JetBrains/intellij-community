// "Replace with collect" "true"

import java.util.*;

public class Test {
  public static LinkedList<String> test(String[] args) {
    Set<String> set = new HashSet<>();
    for(String s : ar<caret>gs) {
      if(!s.isEmpty())
        set.add(s);
    }
    List<String> list = new ArrayList<>(set);
    list.sort(null);
    return new LinkedList<>(list);
  }
}
