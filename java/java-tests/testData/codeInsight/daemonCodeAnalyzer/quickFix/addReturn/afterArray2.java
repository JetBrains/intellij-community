// "Add 'return' statement" "true"
import java.util.*;
class T {
    Object[] f() {
        Set set = new HashSet();
        set.add("a");
        return <caret><selection>set.toArray()</selection>;
    }
}