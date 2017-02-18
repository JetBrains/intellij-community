// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class PrimitiveItem {
    void foo(Iterator<Integer> it) {
        for (Iterator<Integer> it1 = it; it1.hasNext(); ) {
            int i = it1.next();
            System.out.println(i);
        }
    }
}