// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  private static List<String> test(List<List<String>> list) {
      List<String> result = new ArrayList<>();
      for (List<String> strings : list) {
          for (String s : strings) {
              result.add(s);
          }
      }
      return result;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(Arrays.asList("", "a", "abcd", "xyz"), Arrays.asList("x", "y"))));
  }
}