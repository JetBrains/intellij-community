import java.util.Map;
import java.util.Set;

public class A {
    public String get(String k) {
        return buildMap().get(k);
    }

    public Set<String> getAll(String x) {
        return buildMap().keySet();
    }

    public Map<String, String> fo<caret>o() {
      return buildMap();
    }

    public Map<String, String> buildMap() {
        return null;
    }

}