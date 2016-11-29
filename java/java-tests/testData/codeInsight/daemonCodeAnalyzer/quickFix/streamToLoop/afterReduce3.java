// "Replace Stream API chain with loop" "true"

import java.util.List;

import static java.util.Arrays.asList;

public class Main {
  public static int test(List<String> list) {
      Integer acc = 0;
      for (String s : list) {
          acc = acc + s.length();
      }
      return acc;
  }

  public static void main(String[] args) {
    System.out.println(test(asList("a", "b", "c")));
  }
}