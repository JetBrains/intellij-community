// "Replace Stream API chain with loop" "true"

import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  public void test(String... list) {
    DoubleSupplier s = () -> {
        long sum = 0;
        long count = 0;
        for (String s1 : list) {
            if (Objects.nonNull(s1)) {
                sum += s1.length();
                count++;
            }
        }
        return (count == 0 ? 0.0 : (double) sum / count);
    };
    System.out.println(s.getAsDouble());
  }

  public static void main(String[] args) {
    new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs");
  }
}
