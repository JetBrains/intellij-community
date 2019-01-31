// "Fix all 'Optional call chain can be simplified' problems in file" "true"
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class MyClass {
  interface Person {
    Optional<String> name();
  }

  public static void testStream(Person p, Optional<Integer> opt) {
    p.name().map(Stream::of).or<caret>Else(Stream.empty()).forEach(System.out::println);
    p.name().map(n -> Stream.of(n.trim())).orElseGet(() -> Stream.empty()).forEach(System.out::println);
    p.name().map(n -> Stream.of(n.split(""))).orElse(Stream.empty()).forEach(System.out::println);
    p.name().map(n -> IntStream.of(n.length())).orElseGet(IntStream::empty).forEach(System.out::println);
    p.name().map(String::chars).orElseGet(IntStream::empty).forEach(System.out::println);
    p.name().map(n -> n.isEmpty() ? Stream.empty() : Stream.of(n)).orElseGet(Stream::empty).forEach(System.out::println);

    Stream<Integer> stream = opt.map(Stream::of).orElseGet(Stream::empty);
  }
}