// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class Main {
  public static Optional<String> test(List<String> list) {
      boolean seen = false;
      String acc = null;
      for (String s : list) {
          if (!seen) {
              seen = true;
              acc = s;
          } else {
              acc = acc + s;
          }
      }
      return (seen ? Optional.of(acc) : Optional.empty());
  }

  public static void main(String[] args) {
    System.out.println(test(asList("a", "b", "c")));
  }
}