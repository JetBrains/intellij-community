// "Wrap with unmodifiable set" "false"
import java.util.Set;
import java.util.TreeSet;

class C {
    void test() {
        TreeSet<String> result = new TreeSet<>();
        TreeSet<String> other = <caret>result;
    }
}