import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;


public class Collector {
  public int create111() {
    return (int) new ArrayList<String>().stream().map(s -> s + s).count();
  }

  public Stream<String> create() {
    return new ArrayList<String>().stream().map(s -> s + s);
  }

  public Stream<String> create2() {
    return create().limit(12);
  }

  public Stream<String> create3() {
    return create2().limit(12);
  }

  public Optional<String> m(Stream<String> fi, Function<String, String> f1, Function<String, String> f2) {
    return fi.map(f1).map(f2).findFirst();
  }
}