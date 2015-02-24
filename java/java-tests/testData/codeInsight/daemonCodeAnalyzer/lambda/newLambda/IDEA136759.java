
import java.util.function.Function;
import java.util.stream.Stream;

class Test {

  public Long getKey() {
    return 0L;
  }

  public static void main(Stream<Test> stream) {
    stream.map(s -> Inner.of(Test::getKey, s));
  }

  public static final class Inner<K> {
    public static <T> Inner<T> of(final Object key, final Test   value) {return null;}
    public static <T> Inner<T> of(final Function< T, Long> keyMapper, final Test value) {return null;}
  }
}