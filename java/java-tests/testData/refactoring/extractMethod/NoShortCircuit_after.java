public class Test {
    public static void foo(char c) {
        if (newMethod(c) || c == '\u0000') {
            System.out.println("");
        }
    }

    private static boolean newMethod(char c) {
        return c == '\n' || c == '\r';
    }
}
