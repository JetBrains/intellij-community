import org.jetbrains.annotations.NotNull;

interface I {
    String FOO = newMethod();

    static @NotNull String newMethod() {
        return "hello";
    }
}