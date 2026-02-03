// "Wrap with unmodifiable set" "false"
import java.util.*;

class C {
    Set<String> test() {
        SortedSet<String> result = new TreeSet<>();
        return Collections.unmodifiableSor<caret>tedSet(result);
    }
}