// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {

  private static OptionalInt test() {
      for (int x = 0; x < 100; x++) {
          if (x > 50) {
              return OptionalInt.of(x);
          }
      }
      return OptionalInt.empty();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}