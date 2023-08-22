// "Replace 'for each' loop with iterator 'for' loop" "true-preview"
import java.util.Iterator;

public class IncompatibleItemType {
    void foo(Iterator<Integer> it) {
        for (String string : <caret>it) System.out.println(string);
    }
}