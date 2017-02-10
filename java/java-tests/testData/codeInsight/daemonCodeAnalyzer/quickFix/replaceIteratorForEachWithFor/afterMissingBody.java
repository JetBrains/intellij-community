// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class MissingBody {
    void foo(Iterator<Integer> it1) {
        for (Iterator<Integer> it = it1; it.hasNext(); ) {
            Integer integer = it.next();
        }
    }
}