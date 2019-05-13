// "Add 'return' statement" "true"
import java.util.*;
class T {
    int[] f() {
        Set<Integer> set = new HashSet<>();
        set.add(42);
        <caret>}
}