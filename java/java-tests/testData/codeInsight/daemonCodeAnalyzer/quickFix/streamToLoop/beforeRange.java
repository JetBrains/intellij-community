// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.stream.IntStream;

public class Main {
  private static long check(int start, int stop, double v) {
    return IntStream.range(start, stop).mapToDouble(x -> 1.0 / x).filter(x -> x < v).cou<caret>nt();
  }

  private static long checkClosed(int start, double val) {
    return IntStream.rangeClosed(start, start*200).mapToDouble(x -> 1.0 / x).filter(v -> v < val).count();
  }

  public static void main(String[] args) {
    System.out.println(check(1, 100, 0.04));
    System.out.println(checkClosed(2, 0.04));
  }
}