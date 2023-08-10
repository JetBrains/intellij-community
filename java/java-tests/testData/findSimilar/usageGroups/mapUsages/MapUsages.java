import java.util.HashMap;
import java.util.Map;

public class MapUsages {

    public void a() {
        Map<String, String> map =new HashMap<>();
        //init map
        map.put("1", "first");
        map.put("2", "second");
        //verify map
        if (!map.containsKey("1")) {
            map.put("1", "first");
        }
        if (!map.containsKey("2")) {
            map.put("2", "second");
        }
        //clear data
        map.put("1", null);
        map.put("2", null);
    }
}
