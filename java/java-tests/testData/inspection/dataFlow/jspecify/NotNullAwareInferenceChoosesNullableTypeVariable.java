import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;

class NotNullAwareInferenceChoosesNullableTypeVariable {
  @DefaultNonNull
  interface Super {
    <T extends @Nullable Object> void consume(T t);
  }

  abstract class Sub implements Super {
    <E> void go(@Nullable E value) {
      consume(value);
    }
  }
}
