// "Replace Stream API chain with loop" "true-preview"

import java.util.Objects;
import java.util.stream.Stream;

public class Main {
  public void test(String... list) {
    Runnable s = () -> {
        for (String string : list) {
            if (string != null) {
                System.out.println(string);
            }
        }
    };
  }

  public static void main(String[] args) {
    new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs");
  }
}
