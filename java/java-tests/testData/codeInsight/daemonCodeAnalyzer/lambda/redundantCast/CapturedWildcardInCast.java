
import java.util.function.IntFunction;
import java.util.stream.Stream;

class MyTest {
  private static void getArguments(final Stream<Class<String>> classStream) {
    final Class<?>[] classes = classStream.toArray(((<warning descr="Casting '(value) -> {...}' to 'IntFunction<Class<?>[]>' is redundant">IntFunction<Class<?>[]></warning>) (value) -> new Class<?>[value]) );
  }
}
