// "Replace with 'switch' expression" "true-preview"
import org.jetbrains.annotations.Nullable;

class X {
    private static void test(@Nullable Object object) {
        String r = switch (object) {
            case Integer i -> "int = " + i;
            case String s when s.length() > 3 -> s.substring(0, 3);
            case null, default -> "default";
        };
    }
}