import java.util.concurrent.CompletableFuture;

class CompletableFutureTest {

  void foo(CompletableFuture<String> future3, CompletableFuture<String> future1) throws Exception {
    onFailure(future1.thenApply(v -> future3));
  }

  private static <T1> CompletableFuture<T1> onFailure(CompletableFuture<T1> future) {
    return null;
  }
}