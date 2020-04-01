import org.jetbrains.annotations.NotNull;

public class A {
    A(String s) {
    }

    A() {
        this(newMethod());
    }

    @NotNull
    private static String newMethod() {
        return "a";
    }
}