// "Replace with enhanced 'switch' statement" "true"
import org.jetbrains.annotations.Nullable;

class X {
    private static void test(@Nullable Object object) {
        String r;
        switch (object) {
            case Integer i -> r = "int = " + i;
            case String s && s.length() > 3 -> r = s.substring(0, 3);
            case null, default -> {
            }
        }
    }
}