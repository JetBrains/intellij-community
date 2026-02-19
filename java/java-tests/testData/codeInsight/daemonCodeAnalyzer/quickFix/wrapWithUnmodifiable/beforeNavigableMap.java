// "Wrap with unmodifiable map" "true-preview"
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;

class C {
    SortedMap<String, Integer> test() {
        NavigableMap<String, Integer> result = new TreeMap<>();
        return <caret>result;
    }
}