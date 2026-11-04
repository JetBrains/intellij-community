// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    int test(@Nullable String s, int k) {
        return switch (k) {
            case 1 -> {
                yield s.<caret>length();
            }
            default -> 2;
        };
    }
}
