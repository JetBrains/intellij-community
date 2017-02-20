// "Replace Stream API chain with loop" "true"

import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  public void test(String... list) {
    DoubleSupplier s = () -> Stream.of(list)
                               .filter(Objects::nonNull)
                               .co<caret>llect(Collectors.averagingLong(String::length));
    System.out.println(s.getAsDouble());
  }

  public static void main(String[] args) {
    new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs");
  }
}
