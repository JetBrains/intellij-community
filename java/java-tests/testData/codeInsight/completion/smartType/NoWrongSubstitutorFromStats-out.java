import java.util.Comparator;
import java.util.List;
import java.util.Collections;

public class Foo {
    private List<String> myList;

    private void sort() {
        Collections.sort(myList, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                <selection>return 0;</selection>
            }
        });
    }
}
