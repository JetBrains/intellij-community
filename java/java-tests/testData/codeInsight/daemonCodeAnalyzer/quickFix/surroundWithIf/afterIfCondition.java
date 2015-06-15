// "Surround with 'if (i != null)'" "true"
import org.jetbrains.annotations.Nullable;

class A {
    void foo(@Nullable String i) {
        if (i != null && i.length() > 0) {
            if (i != "a") {
            }
        }
    }
}