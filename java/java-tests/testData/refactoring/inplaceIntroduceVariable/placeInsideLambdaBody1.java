import java.util.stream.Stream;

class Test {
  Object f = Stream.of("x").filter(s -> s.chars().anyMatch(c -> c == <selection>s.charAt(0)</selection>)).findFirst();
}
