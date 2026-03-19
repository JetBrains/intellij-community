import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

class B {
    @NullMarked
    static String test(@Nullable String s, String s1) {
      return "";
    }
}
