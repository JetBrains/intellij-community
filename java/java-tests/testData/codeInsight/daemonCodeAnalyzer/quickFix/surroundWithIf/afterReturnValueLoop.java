// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    int test(@Nullable String s, int n) {
        for (int i = 0; i < n; i++) {
            if (s != null) {
                return s.length();
            }
        }
        return -1;
    }
}
