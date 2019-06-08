// "Wrap with unmodifiable set" "true"
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

class C {
    Set<String> test() {
        Set<String> result = new HashSet<>();
        return Collections.unmodifiableSet(result);
    }
}