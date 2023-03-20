// "Replace the loop with 'Collection.removeIf'" "true"
import java.util.*;

public class Main {
    public void removeEmpty(List<String> list) throws Exception {
        for<caret>(String item : list) {
            // Presumably CopyOnWriteArrayList
            if(item.isEmpty()) list.remove(item);
        }
    }
}