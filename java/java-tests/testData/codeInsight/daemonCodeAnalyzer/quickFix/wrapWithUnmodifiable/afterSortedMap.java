// "Wrap with unmodifiable map" "true-preview"
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

class C {
    SortedMap<String, Integer> test() {
        SortedMap<String, Integer> result = new TreeMap<>();
        return Collections.unmodifiableSortedMap(result);
    }
}