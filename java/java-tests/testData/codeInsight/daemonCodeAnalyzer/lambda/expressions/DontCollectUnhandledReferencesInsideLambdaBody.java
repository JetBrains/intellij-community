import java.util.Optional;
import java.util.concurrent.CompletableFuture;

class Test {

  Optional<String> getOptionalAssigneeId() {
    return null;
  }

  public void getById(Optional<Test> join, CompletableFuture<Void> voidCompletableFuture) {
    voidCompletableFuture.thenApply(v -> {
      return join.map(caze -> {
        caze.getOptionalAssigneeId().map(id -> {
          String s =  id;
          return null;
        });
        return null;
      });
    }).join();
  }
}
