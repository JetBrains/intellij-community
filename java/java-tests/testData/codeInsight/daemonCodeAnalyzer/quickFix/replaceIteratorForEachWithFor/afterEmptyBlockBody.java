// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class EmptyBlockBody {
    void foo(Iterator<Integer> it) {
        for (Iterator<Integer> it1 = it; it1.hasNext(); ) {
            Integer integer = it1.next();
        }
    }
}