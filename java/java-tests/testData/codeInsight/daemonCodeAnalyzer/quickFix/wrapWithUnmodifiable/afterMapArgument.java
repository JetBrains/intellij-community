// "Wrap with unmodifiable map" "true"
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

class C {
    void test() {
        var result = new HashMap<>();
        foo(Collections.unmodifiableMap(result));
    }

    void foo(Map<String, Integer> map) {}
}