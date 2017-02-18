// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class Main {
  public static String test(List<String> list) {
      String acc = "";
      for (String s : list) {
          acc = acc + s;
      }
      return acc;
  }

  public static void main(String[] args) {
    System.out.println(test(asList("a", "b", "c")));
  }
}