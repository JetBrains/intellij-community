import org.jetbrains.annotations.NotNull;

interface I {
    String FOO = newMethod();

    @NotNull
    static String newMethod() {
        return "hello";
    }
}