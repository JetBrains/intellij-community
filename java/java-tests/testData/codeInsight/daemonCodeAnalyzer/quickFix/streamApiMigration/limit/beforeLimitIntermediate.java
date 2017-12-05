// "Replace with collect" "true"

import java.util.*;

public class Main {
  public Set<String> test(String[] array) {
    int count = /*initial count*/0;
    Set<String> set = new HashSet<>();
    for(String str : a<caret>rray) {
      if (str != null) {
        set.add(str);
        if(++count == 10) break;
      }
    }
    return set;
  }
}