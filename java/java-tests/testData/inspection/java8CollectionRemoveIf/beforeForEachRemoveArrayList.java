// "Replace the loop with 'Collection.removeIf'" "true"
import java.util.*;

public class Main {
    public void removeEmpty(List<String> list) throws Exception {
        // Copy to avoid CME
        for<caret>(String item : new ArrayList<>(list)) {
            if(item.isEmpty()) list.remove(item);
        }
    }
}