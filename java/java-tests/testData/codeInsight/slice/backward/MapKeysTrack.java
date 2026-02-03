import java.util.*;

class Map5 {
    void f(String s, Map<String, String> m) {
        m.put("x", s);
        Set<String> keys = m.keySet();
        String[] keyArray = keys.toArray(new String[0]);
        String <caret>v = keyArray[0];
    }
}
