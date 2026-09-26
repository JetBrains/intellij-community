// "Wrap with unmodifiable map" "false"
import java.util.Map;
import java.util.HashMap;

class C {
    Map<String, String> map;
    {
        this.<caret>map = new HashMap<>();
    }
}