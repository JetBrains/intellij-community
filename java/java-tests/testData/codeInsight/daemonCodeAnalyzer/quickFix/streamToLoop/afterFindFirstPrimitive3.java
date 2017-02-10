// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {

  private static int test() {
      for (int x = 0; x < 100; x++) {
          if (x > 50) {
              return x;
          }
      }
      return Math.abs(-1);
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}