// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class FinalItem {
    void foo(Iterator<Integer> it) {
        for (Iterator<Integer> iter = it; iter.hasNext(); ) {
            final Integer integer = iter.next();
            System.out.println(integer);
        }
    }
}