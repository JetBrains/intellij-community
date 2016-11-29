// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class CodeBlockBody {
    void foo(Iterator<Integer> it,Iterator<Integer> it1) {
        for (Iterator<Integer> it2 = it1; it2.hasNext(); ) {
            Integer integer = it2.next();
            System.out.println(integer + " a");
            // a comment
            System.out.println(integer + " b");
        }
    }
}