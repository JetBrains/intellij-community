// "Surround with try/catch" "true-preview"
public class Test {
    public static void main(String[] args) {
        Object foo = null;
        try {
            foo = bar();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        foo.toString();
    }

    private static Object bar() throws Exception {
        return null;
    }
}