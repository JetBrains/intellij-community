import org.jspecify.annotations.*;

@NullMarked
class B {
    @NullUnmarked
    static class Nested {
      static @NonNull String test(@Nullable String s, String s1) {
        return s1 == null ? s : s1;
      }
    }
}
