// "Wrap with unmodifiable map" "false"
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

class C {
    Map<String, Integer> test() {
        Map<String, Integer> result = new HashMap<>();
        return Collections.unmodifiab<caret>leMap(result);
    }
}