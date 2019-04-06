// "Wrap with unmodifiable list" "false"
import java.util.List;
import java.util.ArrayList;

class C {
    List<List<String>> test() {
        List<String> result = new ArrayList<>();
        return List.<caret>of(result);
    }
}