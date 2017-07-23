// "Replace Stream API chain with loop" "true"

import java.util.Objects;
import java.util.stream.Stream;

public class Main {
  public void test(String... list) {
    Runnable s = () -> {
        for (String s1 : list) {
            if (s1 != null) {
                System.out.println(s1);
            }
        }
    };
  }

  public static void main(String[] args) {
    new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs");
  }
}
