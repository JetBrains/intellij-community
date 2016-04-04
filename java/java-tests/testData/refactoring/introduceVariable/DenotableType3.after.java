import java.util.function.IntFunction;
import java.util.stream.Stream;

class MyTest {
  private static void getArguments(final Stream<Class<String>> classStream) {
      IntFunction<Class<?>[]> m = (value) -> new Class<?>[value];
      final Class<?>[] classes = classStream.toArray(m);
  }
}
