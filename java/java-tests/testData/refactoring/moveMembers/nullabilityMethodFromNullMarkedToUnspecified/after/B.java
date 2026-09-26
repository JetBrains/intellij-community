import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

class B {
    @NullMarked
    static String test(String s, @Nullable String s1) {
      return s1 == null ? s : s1;
    }
}
