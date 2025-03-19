import org.jetbrains.annotations.NotNull;

public class Test {
    void f1(String p) {
        String r = getLowerCase(p);
    }

    private static @NotNull String getLowerCase(String p) {
        return p.toLowerCase();
    }

    void f2(String p) {
        String r = getLowerCase(p);
    }
}