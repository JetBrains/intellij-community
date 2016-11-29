// "Replace 'for each' loop with iterator 'for' loop" "false"
import java.lang.ref.Reference;

public class NotIterator {
    void foo(Reference<String> it) {
        for (String string : <caret>it) System.out.println(string);
    }
}