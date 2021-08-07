// "Replace with 'switch' expression" "true"
import org.jetbrains.annotations.Nullable;

class X {
    private static void test(@Nullable Object object) {
        String r = switch (object) {
            case Integer i -> "int = " + i;
            case String s && s.length() > 3 -> s.substring(0, 3);
            case null, default -> "default";
        };
    }
}