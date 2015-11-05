import java.util.ArrayList;
import java.util.stream.Stream;

public class Main {
  Stream<String> m1() {
    return new ArrayList<String>().stream().map(s -> s + s);
  }

  java.util.Optional<String> m2() {
    return m1().filter(s -> s.indexOf('s') == 123).findFirst();
  }

  void m3() {
    String target = m2().orElse(null);
  }
}