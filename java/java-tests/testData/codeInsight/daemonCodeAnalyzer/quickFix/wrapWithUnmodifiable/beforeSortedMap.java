// "Wrap with unmodifiable map" "true"
import java.util.SortedMap;
import java.util.TreeMap;

class C {
    SortedMap<String, Integer> test() {
        SortedMap<String, Integer> result = new TreeMap<>();
        return <caret>result;
    }
}