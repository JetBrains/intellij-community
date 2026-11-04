// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    String test(@Nullable String s, int k) {
        return switch (k) {
            case 1 -> {
                if (s != null) {
                    yield s.trim();
                }
                yield null;
            }
            default -> "";
        };
    }
}
