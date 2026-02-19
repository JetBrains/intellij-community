// "Replace 'final String txt' with casts" "true-preview"
import java.util.Map;

public class WithPreview {
    private static void testIfElseIfFalseIf(Object object) {
        if (!(object instanceof String)) {
            return;
        } else {
            final String txt = (String) object;
            System.out.println(txt);
        }
        final String txt = (String) object;
        System.out.println(txt);
    }
}