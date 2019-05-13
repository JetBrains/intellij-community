
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

class Main {

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    Foo foo = first().get();
    System.out.println(foo.getClass());
  }

  static CompletableFuture<Foo> first() {
    return second().thenCompose(maybe -> maybe.map(bar -> CompletableFuture.completedFuture(new Foo()))
      .orElseGet(() -> CompletableFuture.completedFuture(new FooExt())));
  }

  static CompletableFuture<Optional<Bar>> second() {
    return CompletableFuture.completedFuture(Optional.empty());
  }


  static void simplified(final Optional<CompletableFuture<Foo>> future) {
    future.orElseGet(() -> CompletableFuture.completedFuture(new FooExt()));
  }

}

class Foo {
}

class FooExt extends Foo {

}

class Bar {
}