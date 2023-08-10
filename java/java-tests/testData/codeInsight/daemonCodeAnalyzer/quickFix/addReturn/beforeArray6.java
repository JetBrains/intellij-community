// "Add 'return' statement" "true-preview"
import java.util.*;
class A<T> {
    T[] f() {
        Set<T> set = new HashSet<>();
        <caret>}
}