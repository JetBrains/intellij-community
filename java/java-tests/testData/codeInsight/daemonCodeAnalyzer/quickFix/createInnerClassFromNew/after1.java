// "Create Inner Class 'MyCollection'" "true"
import java.util.*;

public class Test {
    public static void main() {
        Collection c = new MyCollection(1, "test");
    }

    private static class MyCollection implements Collection {
        public MyCollection(int i, String test) {<caret>
        }
    }
}