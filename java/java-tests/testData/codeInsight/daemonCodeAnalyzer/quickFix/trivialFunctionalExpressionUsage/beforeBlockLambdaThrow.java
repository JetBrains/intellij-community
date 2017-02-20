// "Replace method call on lambda with lambda body" "false"

import java.util.function.Predicate;

public class Test {
  public static void main(String[] args) {
    ((Predicate<String>)((x) -> {
      if (x.length() > 0) {
        return x.startsWith("a");
      }
      throw new IllegalArgumentException();
    })).<caret>test("ab");
  }
}