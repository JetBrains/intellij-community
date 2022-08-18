import org.jetbrains.annotations.NotNull;

public class Test {
    void f1(String p) {
        String r = getR(p);
    }

    @NotNull
    private static String getR(String p) {
        return p.toLowerCase();
    }

    void f2(String p) {
        String r = getR(p);
    }
}