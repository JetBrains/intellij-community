// "Create class 'MyCollection'" "true"
import java.util.*;

class Test extends Foo {
    public void main() {
        Collection c = new Foo.MyCollection(1);
    }
}

class Foo {
    public class MyCollection implements Collection {
        public MyCollection(int i) {
        }
    }
}