// "Replace with 'switch' expression" "true"
import org.jetbrains.annotations.Nullable;

class X {
    private static void test(@Nullable Object object) {
        String r;
        swi<caret>tch (object) {
            case Integer i:
                r = "int = " + i;
                break;
            case String s && s.length() > 3:
                r = s.substring(0, 3);
                break;
            case null:
            default:
                r = "default";
        }
    }
}