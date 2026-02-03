import java.util.List;
import java.util.Optional;
import java.util.function.Function;

class Main {
  {
    List<Optional<Function<String, String>>> list = asList(of(Main::identity));
  }

  static <T> List<T> asList(T... a) { return null;}

  static <T> Optional<T> of(T value) { return null;}

  public static String identity(final String s) {
    return s;
  }

}