// "Replace Stream API chain with loop" "true"

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  public void test(String... list) {
      double sum = 0;
      long count = 0;
      for (String s : list) {
          if (s != null) {
              sum += 1.0 / s;
              count++;
          }
      }
      System.out.println(count > 0 ? sum / count : 0.0);
  }

  public static void main(String[] args) {
    new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs");
  }
}
