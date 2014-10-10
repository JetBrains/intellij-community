import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static java.util.Arrays.stream;

class Test {
  public static Callable<Bar> foo(final String[] s) {
    return () -> new  Bar() {
      @Override
      public Stream<Number> baz() {
        return zip(stream(s));
      }
    };
  }

  interface Bar {
    Stream<Number> baz();
  }


  public static <L, O> Stream<O> zip(Stream<L> lefts) {
    return null;
  }
}