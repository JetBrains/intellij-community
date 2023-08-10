// "Wrap with unmodifiable list" "true-preview"
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

class C {
    List<String> test(boolean b) {
        List<String> result1 = new ArrayList<>();
        List<String> result2 = new ArrayList<>();
        return b ? <caret>result1 : result2;
    }
}