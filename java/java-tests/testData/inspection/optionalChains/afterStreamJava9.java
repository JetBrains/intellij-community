// "Fix all 'Optional call chain can be simplified' problems in file" "true"
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class MyClass {
  interface Person {
    Optional<String> name();
  }

  public static void testStream(Person p, Optional<Integer> opt) {
    p.name().stream().forEach(System.out::println);
    p.name().stream().map(String::trim).forEach(System.out::println);
    p.name().stream().flatMap(n -> Stream.of(n.split(""))).forEach(System.out::println);
    p.name().stream().mapToInt(String::length).forEach(System.out::println);
    p.name().stream().flatMapToInt(String::chars).forEach(System.out::println);
    p.name().filter(n -> !n.isEmpty()).stream().forEach(System.out::println);
    
    p.name().stream().flatMap(MyClass::createStream);
    p.name().map(m -> createStreamSideEffect(m)).orElse(Stream.empty());

    Stream<Integer> stream = opt.stream();
  }
  
  static Stream<String> createStream(String s) {
    return Stream.of(s, s);
  }
  
  static Stream<String> createStreamSideEffect(String s) {
    System.out.println(s);
    return Stream.of(s, s);
  }
}