// "Replace with 'getOrDefault()' call" "true"
import java.util.Map;

public class Main {
    public void testGetOrDefault(Map<String, String> map, String key, Main other) {
        String a = null, str = map.get(key);
        // before if
        <caret>if(str == null) {
            // comment
            str = "";
            /* after comment */
        }
        System.out.println(str);
    }
}