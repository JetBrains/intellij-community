// "Remove 'flatMap()' call" "true"
import java.util.function.Function;
import java.util.stream.Stream;

class Flat {
  public static Stream<SomeClazz> main(Stream<Stream<SomeClazz>> objectStreams) {
    return objectStreams.flatMap(Stream::distinct);
  }
  private static class SomeClazz {
  }
}