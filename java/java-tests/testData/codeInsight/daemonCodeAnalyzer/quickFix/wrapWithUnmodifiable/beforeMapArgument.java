// "Wrap with unmodifiable map" "false"
import java.util.Map;
import java.util.HashMap;

class C {
    void test() {
        var result = new HashMap<>();
        foo(<caret>result);
    }

    void foo(Map<String, Integer> map) {}
}