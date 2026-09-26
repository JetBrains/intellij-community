// "Create class 'MyCollection'" "true-preview"
import java.util.*;

public class Test {
    public static void main() {
        Collection c = new <caret>MyCollection(1, "test");
    }
}