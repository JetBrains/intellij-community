// "Replace 'final String txt' with casts" "true-preview"
import java.util.Map;

public class WithPreview {
    private static void testIfElseIfFalseIf(Object object) {
        if (!(object instanceof final String <caret>txt)) {
            return;
        } else {
            System.out.println(txt);
        }
        System.out.println(txt);
    }
}