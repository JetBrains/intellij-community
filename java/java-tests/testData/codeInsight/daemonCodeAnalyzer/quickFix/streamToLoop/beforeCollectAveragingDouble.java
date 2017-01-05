// "Replace Stream API chain with loop" "true"

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  public void test(String... list) {
    System.out.println(Stream.of(list).filter(Objects::nonNull).col<caret>lect(Collectors.averagingDouble(s -> 1.0/s)));
  }

  public static void main(String[] args) {
    new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs");
  }
}
