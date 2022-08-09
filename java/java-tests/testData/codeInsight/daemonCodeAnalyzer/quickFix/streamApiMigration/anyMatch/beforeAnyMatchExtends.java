// "Replace with anyMatch()" "true-preview"

import java.util.*;

public class Main {
  public List<? extends CharSequence> getList() {return Arrays.asList("a");}
  public boolean test() {
    for(CharSequence cs : ge<caret>tList()) {
      String s = cs.toString();
      if(!s.isEmpty()) {
        return true;
      }
    }
    return false;
  }
}
