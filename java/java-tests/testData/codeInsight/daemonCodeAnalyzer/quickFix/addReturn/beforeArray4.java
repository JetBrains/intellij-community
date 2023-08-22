// "Add 'return' statement" "true-preview"
import java.util.*;
class T {
    String[] f() {
        List<String> list = Arrays.asList("a", "b");
        String[] arr = {"c", "d"};
        <caret>}
}