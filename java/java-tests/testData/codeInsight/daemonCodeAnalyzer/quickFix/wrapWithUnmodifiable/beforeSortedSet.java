// "Wrap with unmodifiable set" "true"
import java.util.*;

class C {
    Set<String> test() {
        SortedSet<String> result = new TreeSet<>();
        return <caret>result;
    }
}