// "Transform 'a' into final one element array" "true"
import java.util.*;
class Test {
    public void test() {
        final List<String>[] a = new List[]{new ArrayList<>()};
        Runnable r = () -> {
            a[0] = new ArrayList<>();
        };
    }
}
