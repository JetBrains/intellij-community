package a;

import mockapt.Bench;

public class BenchSecond {
  @Bench("Throughput")
  public int second() {
    return 7;
  }
}
