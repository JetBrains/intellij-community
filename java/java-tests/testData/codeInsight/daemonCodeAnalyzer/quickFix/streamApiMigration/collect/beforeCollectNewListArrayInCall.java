// "Replace with toArray" "true"

import java.util.*;

public class Test {
  public static void test(String[] args) {
    Set<String> set = new HashSet<>();
    for(String s : ar<caret>gs) {
      if(!s.isEmpty())
        set.add(s);
    }
    List<String> list = new LinkedList<>(set);
    list.sort(String.CASE_INSENSITIVE_ORDER);
    System.out.println(Arrays.toString(list.toArray(new String[0])));
  }
}
