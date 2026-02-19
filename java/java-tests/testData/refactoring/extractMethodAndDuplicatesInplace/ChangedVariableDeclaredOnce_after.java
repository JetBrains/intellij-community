import org.jetbrains.annotations.NotNull;

public class Test {
    void test(boolean condition) {
        String s = getString();
        if (condition) s = "new";
        System.out.println(s);
    }

    private static @NotNull String getString() {
        String s = "42";
        return s;
    }
}