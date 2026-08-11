import org.jetbrains.annotations.NotNull;

class A {
    final String t;

    A(String t) {
        this.t = getString(t);
    }

    private static @NotNull String getString(String t) {
        return t.toString();
    }
}