// "Fix all 'Simplify Optional call chains' problems in file" "true"
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class MyClass {
  interface Person {
    Optional<String> name();
  }

  public static void testStream(Person p) {
    p.name().stream().flatMap(Stream::of).forEach(System.out::println);
    p.name().stream().map(String::trim).forEach(System.out::println);
    p.name().stream().flatMap(n -> Stream.of(n.split(""))).forEach(System.out::println);
    p.name().stream().mapToInt(String::length).forEach(System.out::println);
    p.name().stream().flatMapToInt(String::chars).forEach(System.out::println);
    p.name().filter(n -> !n.isEmpty()).stream().forEach(System.out::println);
  }
}