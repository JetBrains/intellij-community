import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class Nullness {
  interface Decoder<T extends @Nullable Object> {
    T decode(Object o);
  }

  static <T extends @Nullable Object> Decoder<T> identity(T value) {
    return __ -> /*ca-nullable-to-not-null*/value;
  }

  public static void main() {
    Integer i = 3;
    Decoder<Integer> d = identity(i);
    int j = d.decode(i);
  }
}