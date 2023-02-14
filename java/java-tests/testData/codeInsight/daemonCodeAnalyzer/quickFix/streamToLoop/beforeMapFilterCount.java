// "Replace Stream API chain with loop" "true-preview"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static long countNonEmpty(List<String> input) {
    return input.stream().map(str -> str.trim()).filter(str -> !str.isEmpty()).cou<caret>nt();
  }

  public static void main(String[] args) {
    System.out.println(countNonEmpty(Arrays.asList("a", "", "b", "", "")));
  }
}