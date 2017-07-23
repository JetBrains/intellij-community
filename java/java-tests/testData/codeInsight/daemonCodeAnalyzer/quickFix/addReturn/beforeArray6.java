// "Add 'return' statement" "true"
import java.util.*;
class A<T> {
    T[] f() {
        Set<T> set = new HashSet<>();
        <caret>}
}