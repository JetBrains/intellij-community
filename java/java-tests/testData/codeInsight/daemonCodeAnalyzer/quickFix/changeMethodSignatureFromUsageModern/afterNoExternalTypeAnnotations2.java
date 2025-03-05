// "Add 'Integer' as 1st parameter to method 'perform'" "true" 
import java.util.concurrent.CompletableFuture;

public class FutureWhenDone {
  public void handle(CompletableFuture<Integer> future) {
    future.whenComplete((r, e) -> {
      perform(r);
    });
  }

  private void perform(Integer r) {
    System.out.println();
  }
}