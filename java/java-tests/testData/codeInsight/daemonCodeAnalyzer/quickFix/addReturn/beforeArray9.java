// "Add 'return' statement" "true-preview"
import java.util.*;
class A<T> {
    Object[] f() {
        List<T> list = new ArrayList<>();
        <caret>}
}