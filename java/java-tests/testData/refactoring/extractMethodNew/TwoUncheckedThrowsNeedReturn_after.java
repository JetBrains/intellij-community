import org.jetbrains.annotations.NotNull;

class Test {
    String test(int x) {
        if (x > 0) {
            return "";
        }
        throw newMethod(x);
    }

    private @NotNull RuntimeException newMethod(int x) {
        if (x < 0) {
            return new IllegalArgumentException();
        } else {
            return new IllegalStateException();
        }
    }
}
