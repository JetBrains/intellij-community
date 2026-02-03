// "Wrap with unmodifiable list" "false"
import java.util.List;
import java.util.Collections;

class C {
    List<String> test() {
        List<String> result = Collections.emptyList();
        return <caret>result;
    }
}