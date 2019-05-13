import java.util.Map;

class Map4 {
    void f(String s, Map<String, String> m) {
        m.put(s, "y");
        String <caret>v = m.get(s);
    }
}
