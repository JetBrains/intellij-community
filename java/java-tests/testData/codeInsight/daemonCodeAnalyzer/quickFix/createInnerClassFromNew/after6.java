// "Create Inner Class 'MyCollection'" "true"
public class Test {
    public static void main() {
        Collection[] cc = new MyCollection[10];
    }
<caret>
    private static class MyCollection {
    }
}