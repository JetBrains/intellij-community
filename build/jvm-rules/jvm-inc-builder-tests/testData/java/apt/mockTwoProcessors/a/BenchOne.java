package a;

import mockapt.Bench;

public class BenchOne {
  @Bench("Throughput")
  public int one() {
    return 1;
  }
}
