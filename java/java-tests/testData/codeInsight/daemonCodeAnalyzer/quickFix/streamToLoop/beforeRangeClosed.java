// "Replace Stream API chain with loop" "true"

import java.util.stream.IntStream;

public class Main {
  private static long check(int start, double val) {
    return IntStream.rangeClosed(start, start*200).mapToDouble(x -> 1.0 / x).filter(v -> v < val).cou<caret>nt();
  }

  public static void main(String[] args) {
    System.out.println(check(2, 0.04));
  }
}