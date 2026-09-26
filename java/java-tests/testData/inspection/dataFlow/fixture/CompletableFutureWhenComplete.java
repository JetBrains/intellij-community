import java.util.concurrent.CompletableFuture;
import typeUse.*;

public class CompletableFutureWhenComplete {
  native CompletableFuture<@NotNull String> supply();
  
  void test() {
    supply().whenComplete((s, t) -> {
      if (t != null) {
        System.out.println(t);
      }
      if (s != null) {
        System.out.println(s);
      }
    });
  }
}