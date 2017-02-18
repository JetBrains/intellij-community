// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class IncompatibleItemType {
    void foo(Iterator<Integer> it) {
        for (Iterator<Integer> it1 = it; it1.hasNext(); ) {
            String string = it1.next();
            System.out.println(string);
        }
    }
}