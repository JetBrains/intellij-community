import java.util.concurrent.CompletableFuture;

interface Test {
  CompletableFuture<?> doWork();
}

class Worker {
  Test test;

  public void stuff() {

    test.doWork()
      .exceptionally(t -> null);
  }
}