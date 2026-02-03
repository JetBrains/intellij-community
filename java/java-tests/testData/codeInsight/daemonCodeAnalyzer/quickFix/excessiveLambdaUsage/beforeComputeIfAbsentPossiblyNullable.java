// "Use 'putIfAbsent' method without lambda" "false"
import java.util.Map;

class Test {
    public void test(Map<String, String> map, String key, String value) {
        map.computeIfAbsent(key, k <caret>-> value);
    }
}