import org.jspecify.annotations.*;

@NullMarked
class A {
    static @NonNull String test(@Nullable String s, String s1) {
        return s1 == null ? s : s1;
    }
}
