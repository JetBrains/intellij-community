// "Collapse loop with stream 'findFirst()'" "true-preview"

import java.util.*;

public class Main {
  public Runnable getRunnable(List<String> data) {
    Runnable def = () -> {};
    for(String s : dat<caret>a) {
      if(s.startsWith("xyz")) {
        return s.length() > 2 ? s::trim : System.out::println;
      }
    }
    return def;
  }
}