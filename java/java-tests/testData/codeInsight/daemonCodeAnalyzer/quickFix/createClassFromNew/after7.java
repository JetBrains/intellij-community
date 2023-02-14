// "Create class 'MyCollection'" "true-preview"
import java.util.*;

public class Test {
    public void main() {
        Collection c = new Test.MyCollection(1);
    }

    public class MyCollection implements Collection {
        public MyCollection(int i) {<caret>
        }
    }
}