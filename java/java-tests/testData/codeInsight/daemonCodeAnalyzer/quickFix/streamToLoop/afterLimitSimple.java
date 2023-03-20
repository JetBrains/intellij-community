// "Replace Stream API chain with loop" "true-preview"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Main {
  public static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long limit = 50;
      for (int i = 0; i < 100; i++) {
          if (limit-- == 0) break;
          stat.accept(i);
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}