// "Add 'Integer' as 1st parameter to method 'perform'" "true" 
import java.util.concurrent.CompletableFuture;

public class FutureWhenDone {
  public void handle(CompletableFuture<Integer> future) {
    future.whenComplete((r, e) -> {
      perform(<caret>r);
    });
  }

  private void perform() {
    System.out.println();
  }
}