// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class EmptyBody {
    void foo(Iterator<Integer> it) {
        for (Iterator<Integer> iter = it; iter.hasNext(); ) {
            Integer integer = iter.next();
        }
    }
}