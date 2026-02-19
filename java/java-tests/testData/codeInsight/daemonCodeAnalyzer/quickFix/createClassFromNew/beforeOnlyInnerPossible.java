// "Create class 'MyCollection'" "false"
import java.util.*;

public class Test {
    public static void main() {
        Collection c = new Foo.My<caret>Collection(1);
    }
}

class Foo {}