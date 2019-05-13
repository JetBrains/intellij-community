

import java.util.*;


class Test2 {
  {
    System.out.println(Optional.of(new Either())
                         .map(either -> Test2.foo(either))
                         .orElse("Hello"));
    Optional.of(new Either())
      .map(either -> Test2.foo(either))
      .orElse("Hello");

    System.out.println(Optional.of(new Either())
                         .map(Test2::foo)
                         .orElse("Hello"));
    Optional.of(new Either())
      .map(Test2::foo)
      .orElse("Hello");
  }

  private static <X extends Exception, A> A foo(Either<X, A> either) throws X { return null; }
}

class Either<L, R> { }
