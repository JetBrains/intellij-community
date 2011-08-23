// "Add 'String' as 2nd parameter to method 'set'" "true"
import java.util.List;

public class X<T> {
    private List<T> myList;

    public void set(List<T> list) {
        myList = list;
    }

    public static void aa() {
        X<Integer> x = new X<Integer>();
        x.set<caret>(null, "aaa");
    }
}