// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {

  private static int test() {
    int res = IntStream.range(0, 100).filter(x -> x > 50).findFir<caret>st().orElseGet(() -> Math.abs(-1));
    return res;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}