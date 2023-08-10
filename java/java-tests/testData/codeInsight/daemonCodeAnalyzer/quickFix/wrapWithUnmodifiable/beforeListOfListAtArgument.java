// "Wrap with unmodifiable list" "true-preview"
import java.util.List;
import java.util.ArrayList;

class C {
    List<List<String>> test() {
        List<String> result = new ArrayList<>();
        return List.of(re<caret>sult);
    }
}