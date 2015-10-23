import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Stream;

public class Main21 {
  void m() {
    Stream<String> it = new ArrayList<String>().stream();

    boolean s = it.anyMatch(s1 -> Objects.equals(s1, "asd"));

  }
}
