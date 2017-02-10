// "Use 'putIfAbsent' method without lambda" "true"
import java.util.Map;

class Test {
    public void test(Map<String, String> map, String key) {
        map.computeIfAbsent(key, (/*comment in param*/k) /*comment in arrow*/<caret>-> (/*comment in parens*/"empty"));
    }
}