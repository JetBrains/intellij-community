// "Surround with 'if (i != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    void foo(@Nullable String i) {
        if (i.le<caret>ngth() > 0) {
            if (i != "a") {
            }  
        }
    }
}