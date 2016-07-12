// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class OneStatementBody {
    void foo(Iterator<Integer> it) {
        for (Iterator<Integer> it1 = it; it1.hasNext(); ) {
            Integer integer = it1.next();
            System.out.println(integer);
        }
    }
}