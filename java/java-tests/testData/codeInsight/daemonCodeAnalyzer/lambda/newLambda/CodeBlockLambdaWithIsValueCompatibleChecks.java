import java.util.Optional;

class Test {
  public static void main(String[] args) {
    Optional<Either<IllegalArgumentException, String>> eith =
      Optional.of(new Either<IllegalArgumentException, String>());

    eith.map(either -> {
      String foo = Test.foo(either);
      return foo;
    }).orElse("Hello");

    eith.map(either -> {
      return Test.foo(either);
    }).orElse("Hello");

    eith.map(either -> Test.foo(either)).orElse("Hello");
  }

  private static <X extends Exception, A> A foo(Either<X, A> either) throws X { return null; }
}

class Either<L, R> { }