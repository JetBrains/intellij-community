import java.util.ArrayList;
import java.util.stream.Stream;

public class Test {
  public Stream<String> create() {
    return new ArrayList<String>().stream().map(s -> s + s);
  }

  public Stream<String> create2() {
    return create().limit(12);
  }

  public Stream<String> create3() {
    return create2().limit(12);
  }

}