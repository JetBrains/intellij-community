import org.jetbrains.annotations.NotNull;

interface I {
    String FOO = newMethod();

    @NotNull
    private static String newMethod() {
        return "hello";
    }
}