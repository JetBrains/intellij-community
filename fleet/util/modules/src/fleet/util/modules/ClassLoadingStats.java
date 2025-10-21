package fleet.util.modules;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

final class ClassLoadingStats {
  // record class and resource loading time
  static final Boolean recordLoadingTime = Boolean.getBoolean("idea.record.classloading.stats");
  static final ThreadLocal<Long> doingClassDefineTiming = new ThreadLocal<>();

  static final ClassLoadingMetric open = new ClassLoadingMetric();
  static final ClassLoadingMetric read = new ClassLoadingMetric();

  static final class ClassLoadingMetric {
    final LongAdder time = new LongAdder();
    final LongAdder counter = new LongAdder();

    @Override
    public String toString() {
      return String.format("%dms (%d)", TimeUnit.NANOSECONDS.toMillis(time.sum()), counter.sum());
    }
  }

  static void endReadRecording(long startTime) {
    var time = System.nanoTime() - startTime;
    read.time.add(time);
    read.counter.increment();
  }
}
