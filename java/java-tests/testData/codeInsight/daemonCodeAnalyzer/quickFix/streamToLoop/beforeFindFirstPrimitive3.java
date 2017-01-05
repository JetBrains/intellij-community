// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {

  private static int test() {
    return IntStream.range(0, 100).filter(x -> x > 50).findFir<caret>st().orElseGet(() -> Math.abs(-1));
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}