// "Add 'return' statement" "true-preview"
import java.util.*;
class T {
    int[] f() {
        Set<Integer> set = new HashSet<>();
        set.add(42);
        <caret>}
}