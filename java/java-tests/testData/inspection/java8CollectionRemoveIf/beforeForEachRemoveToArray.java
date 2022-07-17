// "Replace the loop with 'Collection.removeIf'" "true"
import java.util.*;

public class Main {
    public void removeEmpty(List<String> list) throws Exception {
        // Copy to avoid CME
        for<caret>(String item : list.toArray(new String[list.size()])) {
            if(item.isEmpty()) list.remove(item);
        }
    }
}