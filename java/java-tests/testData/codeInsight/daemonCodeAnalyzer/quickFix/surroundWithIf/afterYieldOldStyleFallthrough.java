// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    int test(@Nullable String s, int k, boolean b) {
        return switch (k) {
            case 1:
                if (b) if (s != null) {
                    yield s.length();
                }
            default:
                yield 2;
        };
    }
}
