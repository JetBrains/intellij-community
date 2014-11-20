import java.io.Serializable;
import java.util.function.Supplier;

class Test {
  static class Loader<K> {

    static <K> Loader<K> from(Supplier<K> supplier) {
      return new Loader<>();
    }
  }

  Loader loader = Loader.from((I<String> & Serializable) () -> "");

  interface I<H> extends Supplier<H>{}
}