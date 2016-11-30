// "Use 'putIfAbsent' method without lambda" "true"
import java.util.Map;

class Test {
    public void test(Map<String, String> map, String key) {
        /*comment in param*/
        /*comment in arrow*/
        map.putIfAbsent(key, (/*comment in parens*/"empty"));
    }
}