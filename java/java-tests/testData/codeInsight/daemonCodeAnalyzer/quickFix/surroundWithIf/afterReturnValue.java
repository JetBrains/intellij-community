// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    int test(@Nullable String s) {
        if (s != null) {
            return s.length();
        }
        return 0;
    }
}
