// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Threads(4)
@Warmup(iterations = 0) // This code affects users since the beginning of the application. Users don't warm up our products.
public class EelEarlyAccessFilterBenchmark {
  @State(Scope.Benchmark)
  public static class BenchmarkState {
    final EelEarlyAccessFilter filter = new EelEarlyAccessFilter();
    final String[] pathFixtures;

    public BenchmarkState() {
      pathFixtures = new String[10];
      Random random = new Random(31337);
      for (int i = 0; i < pathFixtures.length; i++) {
        byte[] bytes = new byte[random.nextInt(1023) + 1];
        pathFixtures[i] = new BigInteger(bytes).toString(64);
      }
    }
  }

  @Benchmark
  public void checkOnePathOneTime(BenchmarkState state, Blackhole blackhole) {
    blackhole.consume(state.filter.check(state.pathFixtures[0]));
  }

  @Benchmark
  public void checkOnePathTenTimes(BenchmarkState state, Blackhole blackhole) {
    for (int ignored = 0; ignored < 10; ignored++) {
      blackhole.consume(state.filter.check(state.pathFixtures[0]));
    }
  }

  @Benchmark
  public void checkTenPathsOneTime(BenchmarkState state, Blackhole blackhole) {
    for (int ignored = 0; ignored < 10; ignored++) {
      for (int i = 0; i < 10; i++) {
        blackhole.consume(state.filter.check(state.pathFixtures[i]));
      }
    }
  }
}
