// "Replace with 'Collection.size()'" "true"

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static long test() {
        List<String> s = new ArrayList<>();
        return s.</* unused parameter */String>stream().co<caret>unt();
    }
}
