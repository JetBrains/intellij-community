// "Extract variable 'lc' to 'thenApply' operation" "true"
import java.util.concurrent.CompletableFuture;

public class Test {
  public static void main(String[] args) {
    CompletableFuture.completedFuture("  XYZ  ")
      .thenApply(x -> x.trim()+"|"+x.trim())
      .thenAccept(s -> {
        String <caret>lc = s.toLowerCase();
        System.out.println(lc);
      });
  }
}