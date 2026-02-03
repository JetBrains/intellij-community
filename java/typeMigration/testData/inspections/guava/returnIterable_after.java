import java.util.ArrayList;
import java.util.stream.Collectors;

public class Main14 {

  Iterable<String> m() {
    return new ArrayList<String>().stream().map(s -> s + s).filter(String::isEmpty).collect(Collectors.toList());
  }
}
