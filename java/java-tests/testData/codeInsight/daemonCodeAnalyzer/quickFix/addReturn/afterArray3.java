// "Add 'return' statement" "true"
import java.util.*;
class T {
    A[] f() {
        Queue<B> queue = new ArrayDeque<>();
        queue.add(new B());
        return <caret><selection>queue.toArray(new A[0])</selection>;
    }
}
class A {}
class B extends A {}