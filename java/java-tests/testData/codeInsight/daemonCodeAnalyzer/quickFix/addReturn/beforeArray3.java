// "Add 'return' statement" "true-preview"
import java.util.*;
class T {
    A[] f() {
        Queue<B> queue = new ArrayDeque<>();
        queue.add(new B());
        <caret>}
}
class A {}
class B extends A {}