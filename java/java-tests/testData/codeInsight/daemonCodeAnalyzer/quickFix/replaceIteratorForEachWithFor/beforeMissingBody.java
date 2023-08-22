// "Replace 'for each' loop with iterator 'for' loop" "true-preview"
import java.util.Iterator;

public class MissingBody {
    void foo(Iterator<Integer> it1) {
        for (Integer integer : <caret>it1)
    }
}