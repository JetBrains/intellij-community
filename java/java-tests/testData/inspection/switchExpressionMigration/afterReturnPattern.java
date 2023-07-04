// "Replace with 'switch' expression" "true-preview"
import org.jetbrains.annotations.Nullable;

class X {
    private static String test(@Nullable Object object) {
        return switch (object) {
            case Integer i -> "x = " + i / 2;
            case String s -> "nullable string";
            case null -> "nullable string";
            default -> "default";
        };
    }
}