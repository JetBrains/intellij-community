// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    int test(@Nullable String s, int k) {
        return switch (k) {
            case 1 -> {
                if (s != null) {
                    yield s.length();
                }
                yield 0;
            }
            default -> 2;
        };
    }
}
