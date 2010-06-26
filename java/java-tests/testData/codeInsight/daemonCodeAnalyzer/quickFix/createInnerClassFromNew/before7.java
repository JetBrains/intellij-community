// "Create Inner Class 'Inner'" "false"
import java.util.*;

public class Test {
    public static void main() {
        Collection c = new Test.My<caret>Collection(1);
    }
}