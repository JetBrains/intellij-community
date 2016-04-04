import java.util.stream.Stream;

class Test {
  private static Class<?>[] getArguments(Stream<Class<String>> classStream) {
    return classStream.toArray(Class<?>[]::new);
  }
}