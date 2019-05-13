// "Use 'putIfAbsent' method without lambda" "false"
import java.util.Map;

class Test {
    public String test(Map<String, String> map, String key) {
        return map.computeIfAbsent(key, k <caret>-> ("empty"));
    }
}