// "Wrap with unmodifiable list" "true-preview"
import java.util.List;
import java.util.ArrayList;

class C {
    List<String> test() {
        List<String> result = new ArrayList<>();
        return <caret>result;
    }
}