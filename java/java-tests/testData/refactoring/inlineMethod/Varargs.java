public class Varargs {
    public static String join(String separator, String... texts) {
        return "";
    }

    public static String joinStrings(String separator, String... texts) {
        return join(separator, texts);
    }

    public void foo() {
        String s = <caret>joinStrings("", "i", "d", "e", "a");
    }
}