import java.util.*;
class MyList extends ArrayList {}

class Test {
    public void test() {
        List<? super List> list = new ArrayList<List>();
        list.add(new MyList());
    }
}
