// "Create Inner Class 'MyArrayList'" "true"
import java.util.*;

public class Test {
    public static void main() {
        ArrayList list = new MyArrayList(1, "test");
    }

    private static class MyArrayList extends ArrayList {
        public MyArrayList(int i, String test) {<caret>
        }
    }
}