// "Create inner class 'MyCollection'" "true"
import java.util.*;

public interface I {
    public static void main() {
        Collection c = new MyCollection(1, "test");
    }

    static class MyCollection implements Collection {
        public MyCollection(int i, String test) {
        }
    }
}