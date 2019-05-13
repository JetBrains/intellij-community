// "Replace with findFirst()" "true"

import java.util.*;

public class Main {
  public List<String> getErrors(List<String> data) {
    List<String> def = Collections.singletonList("Not found");
      return data.stream().filter(s -> s.startsWith("xyz")).findFirst().<List<String>>map(s -> s.length() < 10 ? Collections.emptyList() : Arrays.asList()).orElse(def);
  }
}