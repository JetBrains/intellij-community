// "Replace with toArray" "true"

import java.util.*;

public class Test {
  public static String[] test(String[] args) {
    Set<String> set = new HashSet<>();
    for(String s : a<caret>rgs) {
      if(!s.isEmpty())
        set.add(s);
    }
    List<String> list = new ArrayList<>(set);
    list.sort(null);
    return list.toArray(new String[0]);
  }
}
