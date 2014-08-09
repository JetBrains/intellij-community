
import java.util.stream.Collectors;

class Test {
  {
    Collectors.mapping(i -> 1, Collectors.summingInt(s -> s.intValue()));
  }
}