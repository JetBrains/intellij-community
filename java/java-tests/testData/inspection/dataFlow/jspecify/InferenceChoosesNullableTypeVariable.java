import org.jspecify.nullness.NullMarked;
import org.jspecify.nullness.Nullable;

@NullMarked
interface InferenceChoosesNullableTypeVariable {
  <T extends @Nullable Object> void consume(T t);

  default <E> void go(@Nullable E value) {
    consume(value);
  }
}
