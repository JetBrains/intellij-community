// "Create class 'MyCollection'" "true"
import java.util.*;

class Test extends Foo {
    public void main() {
        Collection c = new Foo.My<caret>Collection(1);
    }
}

class Foo {}