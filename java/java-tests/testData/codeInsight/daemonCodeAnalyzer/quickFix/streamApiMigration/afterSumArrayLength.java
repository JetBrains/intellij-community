// "Replace with sum()" "true"

import java.util.Arrays;

public class Main {
  public long test(String[] array) {
      long i = Arrays.stream(array).filter(a -> a.startsWith("xyz")).mapToLong(String::length).sum();
      return i;
  }
}