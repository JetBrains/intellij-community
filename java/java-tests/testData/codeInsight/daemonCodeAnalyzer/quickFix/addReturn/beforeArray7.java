// "Add 'return' statement" "true"
import java.util.*;
class A<T> {
    List<T>[] f(T a, T b) {
        List<List<T>> list = Arrays.asList(Collections.singletonList(a), Collections.singletonList(b));
        <caret>}
}