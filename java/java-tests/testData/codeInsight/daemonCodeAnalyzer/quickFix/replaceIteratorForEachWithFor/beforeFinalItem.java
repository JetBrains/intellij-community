// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class FinalItem {
    void foo(Iterator<Integer> it) {
        for (Integer integer : <caret>it) System.out.println(integer);
    }
}