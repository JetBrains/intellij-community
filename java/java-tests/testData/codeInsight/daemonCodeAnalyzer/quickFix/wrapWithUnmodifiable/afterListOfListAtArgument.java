// "Wrap with unmodifiable list" "true"
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

class C {
    List<List<String>> test() {
        List<String> result = new ArrayList<>();
        return List.of(Collections.unmodifiableList(result));
    }
}