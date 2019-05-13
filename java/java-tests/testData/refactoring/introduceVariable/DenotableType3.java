import java.util.stream.Stream;

class MyTest {
  private static void getArguments(final Stream<Class<String>> classStream) {
    final Class<?>[] classes = classStream.toArray(<selection>(value) -> new Class<?>[value]</selection>);
  }
}
