import org.jspecify.annotations.*;

@NullMarked
class B {
    static @NonNull String test(@Nullable String s, String s1) {
        return s1 == null ? s : s1;
    }
}
