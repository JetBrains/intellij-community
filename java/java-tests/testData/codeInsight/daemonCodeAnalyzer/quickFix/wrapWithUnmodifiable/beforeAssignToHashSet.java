// "Wrap with unmodifiable set" "false"
import java.util.Set;
import java.util.HashSet;

class C {
    void test() {
        HashSet<String> result = new HashSet<>();
        HashSet<String> other;
        other = <caret>result;
    }
}