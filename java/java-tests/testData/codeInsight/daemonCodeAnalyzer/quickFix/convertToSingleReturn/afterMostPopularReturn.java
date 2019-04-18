// "Transform body to single exit-point form" "true"
import java.util.Collections;

class Test {
    List<String> test(int x) {
        List<String> result = Collections.emptyList();
        if (x != 0) {
            int rem = x % 3;
            if (rem != 1) {
                result = Collections.singletonList("foo");
            }
        }
        return result;
    }
}