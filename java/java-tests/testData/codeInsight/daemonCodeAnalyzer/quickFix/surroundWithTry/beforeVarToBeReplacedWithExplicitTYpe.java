// "Surround with try/catch" "true-preview"
public class Test {
    public static void main(String[] args) {
        var foo = b<caret>ar();
        foo.toString();
    }

    private static Object bar() throws Exception {
        return null;
    }
}