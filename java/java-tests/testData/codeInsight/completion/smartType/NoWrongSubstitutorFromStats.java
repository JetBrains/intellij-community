import java.util.List;
import java.util.Collections;

public class Foo {
    private List<String> myList;

    private void sort() {
        Collections.sort(myList, new Comparator<caret>);
    }
}
