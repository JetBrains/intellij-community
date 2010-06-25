// "Create Constructor" "true"
public class Test {
    public static void main() {
        new My<caret>Collection(10);
    }
}

class MyCollection {
    public MyCollection() {
    }
}