// "Replace Stream API chain with loop" "true"

import java.util.*;

import static java.util.Arrays.asList;

public class Main {
  public static boolean test(List<List<String>> list) {
      for (List<String> strings : list) {
          if (Objects.nonNull(strings)) {
              for (String s : strings) {
                  return true;
              }
          }
      }
      return false;
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
  }
}