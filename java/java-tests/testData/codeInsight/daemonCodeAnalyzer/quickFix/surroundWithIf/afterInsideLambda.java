// "Surround with 'if (i != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    void foo(@Nullable String i) {
        Runnable r = () -> {
            if (i != null) {
                i.length();
            }
        };
    }
}