// "Replace 'for each' loop with iterator 'for' loop" "true"
import java.util.Iterator;

public class EmptyBlockBody {
    void foo(Iterator<Integer> it) {
        for (Integer integer : <caret>it) {}
    }
}