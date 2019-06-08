// "Wrap with unmodifiable map" "true"
import java.util.Map;
import java.util.HashMap;

class C {
    Map<String, Integer> test() {
        var result = new HashMap<>();
        return <caret>result;
    }
}