import java.util.ArrayList;
import java.util.stream.Stream;

public class MainFluentIterable {

  Stream<String> m2() {

    Stream<String> it = new ArrayList<String>().stream();

    return it.map(s -> s + s);
  }

  void m3() {
    System.out.println((int) (int) m2().count());
  }
}