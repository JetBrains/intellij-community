// "Replace Stream API chain with loop" "true"

import java.util.Objects;
import java.util.stream.Stream;

public class Main {
  public void test(String... list) {
    Runnable s = () -> Stream.of(list)
                               .filter(Objects::nonNull).for<caret>Each(System.out::println);
  }

  public static void main(String[] args) {
    new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs");
  }
}
