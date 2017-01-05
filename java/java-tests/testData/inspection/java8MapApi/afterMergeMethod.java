// "Replace with 'merge' method call" "true"
import java.util.Map;

public class Main {
    public void testMerge(Map<String, Integer> map, String key, int max) {
        /*get!*/
        // check
        /*get max*/
        map.merge(key, /* passed max value */ max, Math::max)
    }
}