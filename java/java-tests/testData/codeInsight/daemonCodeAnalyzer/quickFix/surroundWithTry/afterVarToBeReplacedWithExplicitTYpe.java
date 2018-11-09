// "Surround with try/catch" "true"
public class Test {
    public static void main(String[] args) {
        Object foo = null;
        try {
            foo = bar();
        } catch (Exception e) {
            e.printStackTrace();
        }
        foo.toString();
    }

    private static Object bar() throws Exception {
        return null;
    }
}