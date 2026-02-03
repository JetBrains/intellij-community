// "Wrap with unmodifiable list" "false"
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

class C {
    List<String> test() {
        List<String> result = new ArrayList<>();
        return Collections.<caret>unmodifiableList(result);
    }
}