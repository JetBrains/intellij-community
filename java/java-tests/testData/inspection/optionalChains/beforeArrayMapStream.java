// "Simplify optional chain to 's.stream()'" "false"
import java.util.Optional;
import java.util.stream.Stream;

public class Test {

  public void test() {
    Optional<String[]> s = Optional.of(new String[] {"1","2","3"});
    Stream<String> s1 = s.map(Stream::of).orE<caret>lse(Stream.empty());
  }
}