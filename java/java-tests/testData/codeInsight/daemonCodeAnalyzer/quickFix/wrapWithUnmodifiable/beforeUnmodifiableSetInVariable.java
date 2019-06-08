// "Wrap with unmodifiable set" "false"
import java.util.*;

class C {
    Set<String> test() {
        Set<String> result = Collections.unmodifiableSortedSet(new TreeSet<>());
        return <caret>result;
    }
}