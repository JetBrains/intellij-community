// "Simplify optional chain to '...isPresent()'" "true"
import java.util.Optional;

public class Test {

  public void test() {
    Optional<String[]> s = Optional.of(new String[] {"1","2","3"});
    Stream<String> s1 = s.map(Stream::of).orElse(Stream.empty());
  }
}