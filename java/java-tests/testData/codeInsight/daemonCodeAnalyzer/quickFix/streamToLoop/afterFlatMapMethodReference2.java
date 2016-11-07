// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  private static List<String> test(List<String[]> list) {
      List<String> result = new ArrayList<>();
      for (String[] strings : list) {
          for (String s : strings) {
              result.add(s);
          }
      }
      return result;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(new String[] {"", "a", "abcd", "xyz"}, new String[] {"x", "y"})));
  }
}