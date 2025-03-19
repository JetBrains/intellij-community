import java.util.Iterator;
import java.util.List;

class MyTest {
    void test(List<? extends String> list) {
        <caret>for (String s : list) {
            System.out.println(s);
        }
        Iterator<? extends String> it = null;
    }
}
