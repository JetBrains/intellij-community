package dfa;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public final class ExhaustivenessWithIntersectionType {

  public static void main(String[] ignoredArgs) {
    CompletableFuture
      .supplyAsync(() -> new Random().nextBoolean() ? new Result.Success() : new Result.Failure())
      .thenApply(res ->
                   switch (res) {
                     case Result.Success _ -> 1;
                     case Result.Failure _ -> 2;
                   })
      .thenAccept(System.out::println);
  }

  sealed interface Result {
    record Success() implements Result { }

    record Failure() implements Result { }
  }
}