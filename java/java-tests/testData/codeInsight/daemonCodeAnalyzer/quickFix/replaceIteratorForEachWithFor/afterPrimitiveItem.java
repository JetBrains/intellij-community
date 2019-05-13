// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class PrimitiveItem {
    void foo(Iterator<Integer> it) {
        for (Iterator<Integer> iter = it; iter.hasNext(); ) {
            int i = iter.next();
            System.out.println(i);
        }
    }
}