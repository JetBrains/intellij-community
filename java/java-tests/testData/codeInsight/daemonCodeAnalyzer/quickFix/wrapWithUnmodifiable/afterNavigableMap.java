// "Wrap with unmodifiable map" "true"
import java.util.Collections;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;

class C {
    SortedMap<String, Integer> test() {
        NavigableMap<String, Integer> result = new TreeMap<>();
        return Collections.unmodifiableSortedMap(result);
    }
}