// "Replace method call on lambda with lambda body" "true-preview"

import java.util.function.Predicate;

public class Test {
  public static void main(String[] args) {
    while(true) {
        System.out.println("hello");
        if (!Collections.singleton("abc").contains("ab")) break;
        System.out.println("hello");
    }
  }
}