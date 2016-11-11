// "Replace with findFirst()" "true"

import java.util.*;

public class Main {
  public Runnable getRunnable(List<String> data) {
    Runnable def = () -> {};
      return data.stream().filter(s -> s.startsWith("xyz")).findFirst().<Runnable>map(s -> s.length() > 2 ? s::trim : System.out::println).orElse(def);
  }
}