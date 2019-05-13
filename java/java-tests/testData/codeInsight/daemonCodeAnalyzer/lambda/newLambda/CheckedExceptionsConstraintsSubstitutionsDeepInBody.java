import java.io.IOException;
import java.util.Optional;

class Test {

  interface Extractor<T> {
    T extractData() throws IOException;
  }

  public static <T> T query(Extractor<T> rse) {
    return null;
  }

  static {
    final Optional<String> query = query(() -> {
      final String type = Optional.<String>empty().orElseThrow(null);
      return null;
    });
  }
}
