package a;

import mockapt.Bench;

public class BenchFirst {
  @Bench("Throughput")
  public int first() {
    return 42;
  }
}
