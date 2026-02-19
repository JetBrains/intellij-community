// "Create inner class 'MyCollection'" "true-preview"
public class Test {
    public static void main() {
        Collection[] cc = new MyCollection[10];
    }

    private static class MyCollection {
    }
}