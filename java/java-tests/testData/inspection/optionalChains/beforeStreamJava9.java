// "Fix all 'Simplify Optional call chains' problems in file" "true"
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class MyClass {
  interface Person {
    Optional<String> name();
  }

  public static void testStream(Person p) {
    p.name().map(Stream::of).or<caret>Else(Stream.empty()).forEach(System.out::println);
    p.name().map(n -> Stream.of(n.trim())).orElseGet(() -> Stream.empty()).forEach(System.out::println);
    p.name().map(n -> Stream.of(n.split(""))).orElse(Stream.empty()).forEach(System.out::println);
    p.name().map(n -> IntStream.of(n.length())).orElseGet(IntStream::empty).forEach(System.out::println);
    p.name().map(String::chars).orElseGet(IntStream::empty).forEach(System.out::println);
    p.name().map(n -> n.isEmpty() ? Stream.empty() : Stream.of(n)).orElseGet(Stream::empty).forEach(System.out::println);
  }
}