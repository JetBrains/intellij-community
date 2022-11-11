// "Replace method call on lambda with lambda body" "true-preview"

import java.util.function.Supplier;

public class Test {
  public static void main(String[] args) {
    Supplier<String> s = () -> {
        System.out.println("hello");
        return "foo";
    };
  }
}