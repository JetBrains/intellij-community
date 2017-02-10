// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.function.IntSupplier;

public class Main {
  public static int test(List<String> strings, IntSupplier supplier) {
    return strings.stream().mapToInt(String::length).mi<caret>n().orElseGet(supplier);
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(), () -> -1));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d"), () -> 2));
  }
}