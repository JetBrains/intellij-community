// "Replace the loop with Collection.removeIf" "true"
import java.util.*;

public class Test {
    void test(List<String> list) {
        ListIterator<String> iterator = list.listIterator();
        while(iterator<caret>.hasNext()) {
            if(iterator.next().isEmpty()) {
                iterator.remove();
            }
        }
    }
}
