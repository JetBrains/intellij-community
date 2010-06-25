// "Create Class 'MyCollection'" "true"
import java.util.*;

public class Test {
    public static void main() {
        Collection c = new Test.MyCollection(1);
    }

    public class MyCollection implements Collection {
        public MyCollection(int i) {<caret>
        }
    }
}