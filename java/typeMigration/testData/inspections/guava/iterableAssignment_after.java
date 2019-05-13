import java.util.ArrayList;
import java.util.stream.Collectors;

public class A {
  void m() {
    Iterable<String> it = new ArrayList<String>().stream().map(s -> s + s).collect(Collectors.toList());
  }
}