import java.util.concurrent.*;

class Test {
  void foo1() {
    ThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(<warning descr="'ScheduledThreadPoolExecutor' should not have zero core threads">0</warning>);
    executor.setCorePoolSize(<warning descr="'ScheduledThreadPoolExecutor' should not have zero core threads">0</warning>);
  }

  void foo2(int corePoolSize) {
    if (corePoolSize != 0) return;
    ThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(<warning descr="'ScheduledThreadPoolExecutor' should not have zero core threads">corePoolSize</warning>);
    executor.setCorePoolSize(<warning descr="'ScheduledThreadPoolExecutor' should not have zero core threads">corePoolSize</warning>);
  }

  void foo3(int corePoolSize) {
    if (corePoolSize == 0) return;
    ThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(corePoolSize);
    executor.setCorePoolSize(corePoolSize);
  }
}