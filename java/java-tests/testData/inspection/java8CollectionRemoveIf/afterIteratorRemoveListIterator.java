// "Replace the loop with Collection.removeIf" "true"
import java.util.*;

public class Test {
    void test(List<String> list) {
        list.removeIf(String::isEmpty);
    }
}
