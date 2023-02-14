// "Replace the loop with 'Collection.removeIf'" "true"
import java.util.*;

public class Test {
    void test(List<String> list) {
        ListIterator<String> iterator = list.listIterator();
        while<caret>(iterator.hasNext()) {
            if(iterator.next().isEmpty()) {
                iterator.remove();
            }
        }
    }
}
