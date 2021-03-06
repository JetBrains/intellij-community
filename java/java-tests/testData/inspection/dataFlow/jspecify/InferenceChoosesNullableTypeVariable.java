import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;

@DefaultNonNull
interface InferenceChoosesNullableTypeVariable {
  <T extends @Nullable Object> void consume(T t);

  default <E> void go(@Nullable E value) {
    consume(value);
  }
}
