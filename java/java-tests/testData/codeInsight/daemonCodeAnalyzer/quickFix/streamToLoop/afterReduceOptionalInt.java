// "Replace Stream API chain with loop" "true"

import java.util.OptionalInt;
import java.util.stream.IntStream;

public class Main {
  private static OptionalInt test() {
      boolean seen = false;
      int acc = 0;
      for (int i : new int[]{}) {
          if (!seen) {
              seen = true;
              acc = i;
          } else {
              acc = acc * i;
          }
      }
      return (seen ? OptionalInt.of(acc) : OptionalInt.empty());
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}