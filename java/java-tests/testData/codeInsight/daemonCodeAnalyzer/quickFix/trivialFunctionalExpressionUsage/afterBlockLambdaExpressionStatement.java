// "Replace method call on lambda with lambda body" "true"

import java.util.function.Predicate;

public class Test {
  public static void main(String[] args) {
      System.out.println("hello");
      Collections.singleton("abc").contains("ab");
  }
}