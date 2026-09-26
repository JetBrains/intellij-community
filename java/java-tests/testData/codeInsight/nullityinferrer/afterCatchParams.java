import org.jetbrains.annotations.NotNull;

public class Infer {
    static public boolean a(@NotNull String s) {
        try {
            return s.length() > 0;
        } catch (final NullPointerException e) {
            e.printStackTrace();
            return false;
        }
    }

}