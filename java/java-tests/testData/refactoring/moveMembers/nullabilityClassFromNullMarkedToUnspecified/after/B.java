import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

class B {
    @NullMarked
    static class Nested {
      static @NonNull String test(@Nullable String s, String s1) {
        return s1 == null ? s : s1;
      }
    }
}
