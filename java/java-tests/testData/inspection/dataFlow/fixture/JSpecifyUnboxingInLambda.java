import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

// IDEA-364343
@NullMarked
class AnotherActivity {
  public interface ThrowingFunction<T1 extends @Nullable Object, T2 extends @Nullable Object> {
    T2 apply(T1 input) throws Throwable;
  }

  abstract static class Decoder<T extends @Nullable Object> {
    abstract <T2 extends @Nullable Object> Decoder<T2> then(
      ThrowingFunction<? super T, ? extends T2> dataTransform);
  }

  native Decoder<Boolean> foo();

  Decoder<Boolean> doWork() {
    return foo().then(f -> !f);
  }
}