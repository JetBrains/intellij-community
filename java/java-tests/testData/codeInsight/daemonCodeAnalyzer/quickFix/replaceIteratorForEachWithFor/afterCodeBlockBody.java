// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class CodeBlockBody {
    void foo(Iterator<Integer> it,Iterator<Integer> it1) {
        for (Iterator<Integer> iter = it1; iter.hasNext(); ) {
            Integer integer = iter.next();
            System.out.println(integer + " a");
            // a comment
            System.out.println(integer + " b");
        }
    }
}