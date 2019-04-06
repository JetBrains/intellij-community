// "Wrap with unmodifiable map" "false"
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;

class C {
    NavigableMap<String, Integer> test() {
        NavigableMap<String, Integer> result = new TreeMap<>();
        return <caret>result;
    }
}