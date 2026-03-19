import org.jetbrains.annotations.NotNull;

public class Test {
    void test(boolean condition) {
        String s = getS();
        if (condition) s = "new";
        System.out.println(s);
    }

    private static @NotNull String getS() {
        String s = "42";
        return s;
    }
}