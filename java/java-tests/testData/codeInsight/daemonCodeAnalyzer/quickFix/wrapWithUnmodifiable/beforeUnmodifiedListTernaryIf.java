// "Wrap with unmodifiable list" "false"
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

class C {
    List<String> test(boolean b) {
        List<String> result1 = new ArrayList<>();
        List<String> result2 = new ArrayList<>();
        return Collections.unmodifiableList(b ? <caret>result1 : result2);
    }
}