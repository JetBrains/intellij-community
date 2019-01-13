// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class IncompatibleItemType {
    void foo(Iterator<Integer> it) {
        for (Iterator<Integer> iter = it; iter.hasNext(); ) {
            String string = iter.next();
            System.out.println(string);
        }
    }
}