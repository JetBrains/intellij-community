// "Create inner class 'MyCollection'" "true"
import java.util.*;

public interface I {
    public static void main() {
        class C {
            {
                Collection c = new <caret>MyCollection(1, "test");
            }
        }
    }
}