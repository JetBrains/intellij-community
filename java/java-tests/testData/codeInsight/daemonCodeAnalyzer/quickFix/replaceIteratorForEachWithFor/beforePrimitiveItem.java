// "Replace 'for each' loop with iterator 'for' loop" "true-preview"
import java.util.Iterator;

public class PrimitiveItem {
    void foo(Iterator<Integer> it) {
        for (int i : <caret>it) System.out.println(i);
    }
}