import org.jetbrains.annotations.NotNull;

public class A {
    A(String s) {
    }

    A() {
        this(newMethod());
    }

    private static @NotNull String newMethod() {
        return "a";
    }
}