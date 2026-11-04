// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;

class A {
    void test(@Nullable String s) {
        Supplier<String> sup = () -> s.<caret>trim();
    }
}
