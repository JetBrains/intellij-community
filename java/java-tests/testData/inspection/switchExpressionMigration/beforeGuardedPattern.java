// "Replace with enhanced 'switch' statement" "true-preview"
import org.jetbrains.annotations.NotNull;

class X {
    private static void test(@NotNull Object object) {
        swit<caret>ch (object) {
            case Integer i:
                // line contains no height
                System.out.println(i+1);
                break;
            case String s when !s.isEmpty():
                // line contains no code
                System.out.println("Goodbye.");
                break;
            case null:
                System.out.println("c");
                break;
            default:
                System.out.println("default");
                break;
        }
    }
}