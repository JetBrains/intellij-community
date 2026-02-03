import java.util.ArrayList;
import java.util.stream.Stream;

public class Main10 {

  void m() {
    Stream<String> it = new ArrayList<String>().stream();

    String[] arr = it.map(s -> s + "asd").toArray(String[]::new);
  }
}
