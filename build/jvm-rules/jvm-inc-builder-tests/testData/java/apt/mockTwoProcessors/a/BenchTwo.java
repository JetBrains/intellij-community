package a;

import mockapt.Bench;

public class BenchTwo {
  @Bench("Throughput")
  public int two() {
    return 2;
  }
}
