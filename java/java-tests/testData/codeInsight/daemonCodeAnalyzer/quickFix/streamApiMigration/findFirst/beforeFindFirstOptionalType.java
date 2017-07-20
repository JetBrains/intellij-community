// "Replace with findFirst()" "true"

import java.util.*;

public class Main {
  public List<String> getErrors(List<String> data) {
    List<String> def = Collections.singletonList("Not found");
    for(String s : dat<caret>a) {
      if(s.startsWith("xyz")) {
        return s.length() < 10 ? Collections.emptyList() : Arrays.asList();
      }
    }
    return def;
  }
}