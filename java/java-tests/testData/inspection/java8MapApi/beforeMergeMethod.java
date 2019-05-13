// "Replace with 'merge' method call" "true"
import java.util.Map;

public class Main {
    public void testMerge(Map<String, Integer> map, String key, int max) {
        Integer val = /*get!*/map.get(key);
        // check
        if(val<caret> == null) {
            map.put(key, /* passed max value */ max)
        } else {
            map.put(key, Math.max(/*get max*/val, max));
        }
    }
}