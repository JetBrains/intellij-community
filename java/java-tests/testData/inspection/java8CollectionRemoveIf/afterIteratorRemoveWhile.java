// "Replace the loop with 'Collection.removeIf'" "true"
import java.util.*;

public class Main {
    public void removeEmpty(List<String> list) throws Exception {
        // remove empty
        // everything is ok!
        list.removeIf(str -> str.trim()/*trimmed empty*/
                .isEmpty());
    }
}