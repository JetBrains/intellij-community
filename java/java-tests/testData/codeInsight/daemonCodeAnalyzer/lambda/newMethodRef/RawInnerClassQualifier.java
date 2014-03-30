import java.util.Collections;
import java.util.Map;
import java.util.Optional;


class StreamMain {
  public static void main(final String... args) {
    x(Collections.<Integer, String>emptyMap().entrySet().stream().
      filter(entry -> 0 == entry.getKey() % 2).
      findFirst().
      map(Map.Entry::getValue).
      orElse("Bob!"), true);
  }

  public static void x(final String s, final boolean b) {
    System.out.println(s);
  }
}

class StreamMainSimplified {
  public static void main(Optional<Map.Entry<Integer, String>> first) {
    String s = first.map(Map.Entry::getValue).orElse("Bob!");
    String s1 = first.map((e) -> e.getValue()).orElse("Bob!");
  }

}