import org.jspecify.nullness.NullMarked;
import org.jspecify.nullness.Nullable;

class NotNullAwareInferenceChoosesNullableTypeVariable {
  @NullMarked
  interface Super {
    <T extends @Nullable Object> void consume(T t);
  }

  abstract class Sub implements Super {
    <E> void go(@Nullable E value) {
      consume(value);
    }
  }
}
