// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  public static int test(List<String> list) {
      int sum = 0;
      for (String s : list) {
          System.out.println(s);
          int i = s.length();
          sum += i;
      }
      return sum;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("aaa", "b", "cc", "dddd")));
  }
}