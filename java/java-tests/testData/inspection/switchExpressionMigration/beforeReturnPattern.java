// "Replace with 'switch' expression" "true-preview"
import org.jetbrains.annotations.Nullable;

class X {
    private static String test(@Nullable Object object) {
        swi<caret>tch (object) {
            case Integer i:
                return "x = " + i/2;
            case String s:
            case null:
                return "nullable string";
            default:
                return "default";
        }
    }
}