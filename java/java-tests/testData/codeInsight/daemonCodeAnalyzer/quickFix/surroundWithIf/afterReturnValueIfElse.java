// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    int test(@Nullable String s) {
        if (s != null) {
            if (s.isEmpty()) return 1;
            else return 2;
        }
        return 0;
    }
}
