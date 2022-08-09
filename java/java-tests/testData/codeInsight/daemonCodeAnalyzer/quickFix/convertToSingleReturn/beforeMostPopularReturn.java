// "Transform body to single exit-point form" "true-preview"
import java.util.Collections;

class Test {
    List<String> <caret>test(int x) {
        if (x == 0) return Collections.emptyList();
        int rem = x % 3;
        if (rem == 1) return Collections.emptyList();
        return Collections.singletonList("foo");
    }
}