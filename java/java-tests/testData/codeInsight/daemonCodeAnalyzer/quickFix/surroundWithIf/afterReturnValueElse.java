// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    boolean test(@Nullable String s, boolean b) {
        if (b) return true;
        else {
            if (s != null) {
                return s.isEmpty();
            }
            return false;
        }
    }
}
