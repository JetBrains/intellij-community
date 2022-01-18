import java.util.stream.Stream;

class Test {
  Object f = Stream.of("x").filter(s -> {
      char first = s.charAt(0);
      return s.chars().anyMatch(c -> c == first);
  }).findFirst();
}
