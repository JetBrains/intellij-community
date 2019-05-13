// "Create inner class 'MyCollection'" "true"
import java.util.*;

public interface I {
    public static void main() {
        class C {
            {
                Collection c = new MyCollection(1, "test");
            }

            class MyCollection implements Collection {
                public MyCollection(int i, String test) {
                }
            }
        }
    }
}