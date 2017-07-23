// "Replace method call on lambda with lambda body" "false"

import java.util.function.Predicate;

public class Test {
  public static void main(String[] args) {
    while(((Predicate<String>) ((x) -> {
      System.out.println("hello");
      return Collections.singleton("abc").contains(x);
    })).<caret>test("ab")) {
      System.out.println("hello");
    }
  }
}