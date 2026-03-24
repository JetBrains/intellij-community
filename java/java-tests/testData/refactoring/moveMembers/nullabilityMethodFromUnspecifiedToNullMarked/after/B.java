import org.jspecify.annotations.*;

@NullMarked
class B {
    @NullUnmarked
    static @NonNull String test(@NonNull String s, String s1) {
      return s1 == null ? s : s1;
    }
}
