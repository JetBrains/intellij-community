// "Replace method call on lambda with lambda body" "true"

import java.util.function.Predicate;

public class Test {
  public static void main(String[] args) {
    ((Predicate<String>) ((x) -> {
      System.out.println("hello");
      return Collections.singleton("abc").contains(x);
    })).<caret>test("ab");
  }
}