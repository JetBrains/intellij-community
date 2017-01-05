// "Replace Stream API chain with loop" "true"

import java.util.stream.IntStream;

public class Main {
  private static long check(int start, int stop, double v) {
    return IntStream.range(start, stop).mapToDouble(x -> 1.0 / x).filter(x -> x < v).cou<caret>nt();
  }

  public static void main(String[] args) {
    System.out.println(check(1, 100, 0.04));
  }
}