// "Replace with anyMatch()" "true-preview"

import java.util.*;

public class Main {
  public List<? extends CharSequence> getList() {return Arrays.asList("a");}
  public boolean test() {
      return getList().stream().map(CharSequence::toString).anyMatch(s -> !s.isEmpty());
  }
}
