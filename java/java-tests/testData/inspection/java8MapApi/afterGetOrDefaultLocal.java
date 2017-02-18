// "Replace with 'getOrDefault' method call" "true"
import java.util.Map;

public class Main {
    public void testGetOrDefault(Map<String, String> map, String key, Main other) {
        String a = null, str = map.getOrDefault(key, "");
        // before if
        // comment
        /* after comment */
        System.out.println(str);
    }
}