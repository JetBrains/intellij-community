import java.util.*;
public class MyList extends ArrayList {}

public class Test {
    public void test() {
        List<? super List> list = new ArrayList<List>();
        list.add(new MyList());
    }
}
