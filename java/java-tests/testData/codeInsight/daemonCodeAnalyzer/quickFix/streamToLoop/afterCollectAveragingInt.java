// "Replace Stream API chain with loop" "true"

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  public void test(String... list) {
      long sum = 0;
      long count = 0;
      for (String s : list) {
          if (Objects.nonNull(s)) {
              sum += s.length();
              count++;
          }
      }
      System.out.println(count == 0 ? 0.0 : (double) sum / count);
  }

  public static void main(String[] args) {
    new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs");
  }
}
