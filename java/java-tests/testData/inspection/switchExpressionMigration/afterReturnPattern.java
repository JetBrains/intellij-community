// "Replace with 'switch' expression" "true"
import org.jetbrains.annotations.Nullable;

class X {
    private static String test(@Nullable Object object) {
        return switch (object) {
            case Integer i -> "x = " + i / 2;
            case String s, null -> "nullable string";
            default -> "default";
        };
    }
}