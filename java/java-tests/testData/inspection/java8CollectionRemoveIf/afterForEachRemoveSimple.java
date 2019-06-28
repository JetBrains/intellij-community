// "Replace the loop with Collection.removeIf" "true"
import java.util.*;

public class Main {
    public void removeEmpty(List<String> list) throws Exception {
        // Presumably CopyOnWriteArrayList
        list.removeIf(String::isEmpty);
    }
}