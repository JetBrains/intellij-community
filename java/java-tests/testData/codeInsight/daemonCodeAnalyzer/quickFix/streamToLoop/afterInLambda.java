// "Replace Stream API chain with loop" "true-preview"

import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  public void test(String... list) {
    DoubleSupplier s = () -> {
        long sum = 0;
        long count = 0;
        for (String string : list) {
            if (string != null) {
                sum += string.length();
                count++;
            }
        }
        return count > 0 ? (double) sum / count : 0.0;
    };
    System.out.println(s.getAsDouble());
  }

  public static void main(String[] args) {
    new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs");
  }
}
