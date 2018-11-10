// "Transform 'a' into final one element array" "true"
import java.util.*;
class Test {
    public void test() {
        List<String> a = new ArrayList<>();
        Runnable r = () -> {
            <caret>a = new ArrayList<>();
        };
    }
}
