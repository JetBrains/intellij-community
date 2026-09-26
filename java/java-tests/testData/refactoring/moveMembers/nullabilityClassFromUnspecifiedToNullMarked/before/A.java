import org.jspecify.annotations.*;

class A {
    static class Nested {
      static @NonNull String test(@Nullable String s, String s1) {
        return s1 == null ? s : s1;
      }
    }
}
